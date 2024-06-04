/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.apiplatform.modules.submissions.services

import java.time.Clock
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import cats.data.{NonEmptyList, OptionT, Validated}

import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.commands.applications.domain.models.ApplicationCommands
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{Actors, ApplicationId}
import uk.gov.hmrc.apiplatform.modules.common.services.{ClockNow, EitherTHelper}
import uk.gov.hmrc.apiplatform.modules.submissions.connectors.ThirdPartyApplicationSubmissionsConnector
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models._
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.{ApplicationCommandConnector, DeskproConnector}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.ApplicationUpdateSuccessful
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Application
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.{DeskproTicket, TicketResult}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.DeveloperSession

@Singleton
class RequestProductionCredentials @Inject() (
    tpaConnector: ThirdPartyApplicationSubmissionsConnector,
    deskproConnector: DeskproConnector,
    applicationCommandConnector: ApplicationCommandConnector,
    val clock: Clock
  )(implicit val ec: ExecutionContext
  ) extends ClockNow {


  def requestProductionCredentials(
      applicationId: ApplicationId,
      requestedBy: DeveloperSession,
      app: Application,
      submission: Submission
    )(implicit hc: HeaderCarrier
    ): Future[Either[ErrorDetails, Application]] = {
    val requesterIsResponsibleIndividual = isRequesterResponsibleIndividual(submission)
    val isNewTouUplift                   = submission.context.getOrElse(AskWhen.Context.Keys.NEW_TERMS_OF_USE_UPLIFT, "No") == "Yes"
    if (isNewTouUplift) {
      requestTermsOfUseApproval(applicationId, requestedBy, requesterIsResponsibleIndividual, isNewTouUplift)
    } else {
      requestProductionCredentialsApproval(applicationId, requestedBy, app, requesterIsResponsibleIndividual, isNewTouUplift)
    }
  }

  def isRequesterResponsibleIndividual(submission: Submission) = {
    val responsibleIndividualIsRequesterId = submission.questionIdsOfInterest.responsibleIndividualIsRequesterId
    submission.latestInstance.answersToQuestions.get(responsibleIndividualIsRequesterId) match {
      case Some(ActualAnswer.SingleChoiceAnswer(answer)) => answer == "Yes"
      case _                                             => false
    }
  }

  private def requestProductionCredentialsApproval(
      applicationId: ApplicationId,
      requestedBy: DeveloperSession,
      app: Application,
      requesterIsResponsibleIndividual: Boolean,
      isNewTouUplift: Boolean
    )(implicit hc: HeaderCarrier
    ): Future[ApplicationUpdateSuccessful] = {
    val cmd = ApplicationCommands.SubmitApplicationApprovalRequest(Actors.AppCollaborator(requestedBy.email), instant(), requestedBy.displayedName, requestedBy.email)
      for {
        result     <- applicationCommandConnector.dispatch(applicationId, cmd, Set.empty).map(_ => ApplicationUpdateSuccessful)
        _          <- createDeskproTicketIfNeeded(app, requestedBy, requesterIsResponsibleIndividual, isNewTouUplift, false)
      } yield result
  }

  private def requestTermsOfUseApproval(
      applicationId: ApplicationId,
      requestedBy: DeveloperSession,
      requesterIsResponsibleIndividual: Boolean,
      isNewTouUplift: Boolean
    )(implicit hc: HeaderCarrier
    ): Future[Either[ErrorDetails, Application]] = {

    val ET = EitherTHelper.make[ErrorDetails]
    (
      for {
        app        <- ET.fromEitherF(tpaConnector.requestApproval(applicationId, requestedBy.displayedName, requestedBy.email))
        submission <- ET.fromOptionF(tpaConnector.fetchLatestSubmission(applicationId), ErrorDetails("submitSubmission001", s"No submission record found for ${applicationId}"))
        _          <- ET.liftF(createDeskproTicketIfNeeded(app, requestedBy, requesterIsResponsibleIndividual, isNewTouUplift, submission.status.isGranted))
      } yield app
    )
      .value
  }

  private def createDeskproTicketIfNeeded(
      app: Application,
      requestedBy: DeveloperSession,
      requesterIsResponsibleIndividual: Boolean,
      isNewTouUplift: Boolean,
      isSubmissionPassed: Boolean
    )(implicit hc: HeaderCarrier
    ): Future[Option[TicketResult]] = {
    if (requesterIsResponsibleIndividual) {
      if (isNewTouUplift) {
        if (isSubmissionPassed) {
          // Don't create a Deskpro ticket if the submission passed when it was automatically marked
          Future.successful(None)
        } else {
          val ticket = DeskproTicket.createForTermsOfUseUplift(requestedBy.displayedName, requestedBy.email, app.name, app.id)
          deskproConnector.createTicket(Some(requestedBy.developer.userId), ticket).map(Some(_))
        }
      } else {
        val ticket = DeskproTicket.createForRequestProductionCredentials(requestedBy.displayedName, requestedBy.email, app.name, app.id)
        deskproConnector.createTicket(Some(requestedBy.developer.userId), ticket).map(Some(_))
      }
    } else {
      // Don't create a Deskpro ticket if the requester is not the responsible individual
      Future.successful(None)
    }
  }
}
