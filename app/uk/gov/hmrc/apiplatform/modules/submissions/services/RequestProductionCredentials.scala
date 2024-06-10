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
import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}

import cats.data.NonEmptyList

import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.commands.applications.domain.models.{ApplicationCommands, DispatchSuccessResult, _}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.Actors
import uk.gov.hmrc.apiplatform.modules.common.services.{ApplicationLogger, ClockNow, EitherTHelper}
import uk.gov.hmrc.apiplatform.modules.submissions.connectors.ThirdPartyApplicationSubmissionsConnector
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models.ErrorDetails
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.{ApmConnector, ApplicationCommandConnector, DeskproConnector}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Application
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.{DeskproTicket, TicketResult}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.DeveloperSession

@Singleton
class RequestProductionCredentials @Inject() (
    apmConnector: ApmConnector,
    tpaConnector: ThirdPartyApplicationSubmissionsConnector,
    applicationCommandConnector: ApplicationCommandConnector,
    deskproConnector: DeskproConnector,
    val clock: Clock
  )(implicit val ec: ExecutionContext
  ) extends ClockNow with ApplicationLogger {

  def requestProductionCredentials(
      application: Application,
      requestedBy: DeveloperSession,
      requesterIsResponsibleIndividual: Boolean,
      isNewTouUplift: Boolean
    )(implicit hc: HeaderCarrier
    ): Future[Either[ErrorDetails, Application]] = {

    def getCommand() = {
      if (isNewTouUplift) {
        ApplicationCommands.SubmitTermsOfUseApproval(Actors.AppCollaborator(requestedBy.email), instant(), requestedBy.displayedName, requestedBy.email)
      } else {
        ApplicationCommands.SubmitApplicationApprovalRequest(Actors.AppCollaborator(requestedBy.email), instant(), requestedBy.displayedName, requestedBy.email)
      }
    }

    def handleCmdSuccess(app: Application) = {
      val ET = EitherTHelper.make[ErrorDetails]
      (for {
        submission <- ET.fromOptionF(tpaConnector.fetchLatestSubmission(app.id), ErrorDetails("submitSubmission001", s"No submission record found for ${app.id}"))
        _          <- ET.liftF(createDeskproTicketIfNeeded(app, requestedBy, requesterIsResponsibleIndividual, isNewTouUplift, submission.status.isGranted))
      } yield app).value

    }

    def handleCmdResult(app: Application, dispatchResult: Either[NonEmptyList[CommandFailure], DispatchSuccessResult]) = {
      dispatchResult match {
        case Right(_)                                   => handleCmdSuccess(app)
        case Left(errors: NonEmptyList[CommandFailure]) =>
          errors.map(error => logger.warn(s"commmand failure: ${error.toString()}"))
          val errString = errors.toList.map(_.toString()).mkString(", ")
          successful(Left(ErrorDetails("submitSubmission001", s"Submission failed for ${app.id} - $errString")))
      }
    }

    for {
      dispatchResult <- applicationCommandConnector.dispatch(application.id, getCommand(), Set.empty)
      result         <- handleCmdResult(application, dispatchResult) // if passed then continue else deal with errors

    } yield result

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
