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

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.common.services.EitherTHelper
import uk.gov.hmrc.apiplatform.modules.submissions.connectors.ThirdPartyApplicationSubmissionsConnector
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models.{
  ErrorDetails,
  ResponsibleIndividualToUVerification,
  ResponsibleIndividualTouUpliftVerification,
  ResponsibleIndividualVerification,
  Submission
}
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.DeskproConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.DeskproTicket
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.ApplicationService

@Singleton
class ResponsibleIndividualVerificationService @Inject() (
    tpaSubmissionsConnector: ThirdPartyApplicationSubmissionsConnector,
    applicationService: ApplicationService,
    deskproConnector: DeskproConnector
  )(implicit val ec: ExecutionContext
  ) {

  private val ET = EitherTHelper.make[ErrorDetails]

  def fetchResponsibleIndividualVerification(code: String)(implicit hc: HeaderCarrier): Future[Option[ResponsibleIndividualVerification]] = {
    tpaSubmissionsConnector.fetchResponsibleIndividualVerification(code)
  }

  def getIsGranted(maybeSubmission: Option[Submission]): Boolean = {
    maybeSubmission match {
      case Some(sub) => sub.status.isGranted
      case _         => false
    }
  }

  def accept(code: String)(implicit hc: HeaderCarrier): Future[Either[ErrorDetails, ResponsibleIndividualVerification]] = {
    (
      for {
        riVerification <- ET.fromOptionF(
                            tpaSubmissionsConnector.fetchResponsibleIndividualVerification(code),
                            ErrorDetails("riverification001", s"No responsibleIndividualVerification record found for ${code}")
                          )
        application    <- ET.fromOptionF(
                            applicationService.fetchByApplicationId(riVerification.applicationId),
                            ErrorDetails("riverification002", s"No application record found for ${riVerification.applicationId}")
                          )
        _              <- ET.liftF(applicationService.acceptResponsibleIndividualVerification(riVerification.applicationId, code))
        submission     <- ET.liftF(tpaSubmissionsConnector.fetchLatestSubmission(riVerification.applicationId))
        _              <- ET.liftF(sendDeskproTicketForTermsOfUse(
                            riVerification,
                            application.application.state.requestedByName,
                            application.application.state.requestedByEmailAddress.map(_.toLaxEmail),
                            getIsGranted(submission)
                          ))
      } yield riVerification
    )
      .value
  }

  def decline(code: String)(implicit hc: HeaderCarrier): Future[Either[ErrorDetails, ResponsibleIndividualVerification]] = {
    (
      for {
        riVerification <- ET.fromOptionF(
                            tpaSubmissionsConnector.fetchResponsibleIndividualVerification(code),
                            ErrorDetails("riverification003", s"No responsibleIndividualVerification record found for ${code}")
                          )
        _              <- ET.liftF(applicationService.declineResponsibleIndividualVerification(riVerification.applicationId, code))
      } yield riVerification
    )
      .value
  }

  private def sendDeskproTicketForTermsOfUse(
      riVerification: ResponsibleIndividualVerification,
      submitterName: Option[String],
      submitterEmail: Option[LaxEmailAddress],
      isSubmissionPassed: Boolean
    )(implicit hc: HeaderCarrier
    ) = {
    riVerification match {
      case riv: ResponsibleIndividualToUVerification        => {
        val appName                         = riVerification.applicationName
        val appId                           = riVerification.applicationId
        val requesterName: String           = submitterName.getOrElse(throw new RuntimeException("requestedByName not found"))
        val requesterEmail: LaxEmailAddress = submitterEmail.getOrElse(throw new RuntimeException("requestedByEmailAddress not found"))

        val ticket = DeskproTicket.createForRequestProductionCredentials(requesterName, requesterEmail, appName, appId)
        deskproConnector.createTicket(riVerification.id, ticket).map(Some(_))
      }
      case riuv: ResponsibleIndividualTouUpliftVerification => {
        if (isSubmissionPassed) {
          // Don't create a Deskpro ticket if the submission passed when it was automatically marked
          Future.successful(None)
        } else {
          val appName                         = riVerification.applicationName
          val appId                           = riVerification.applicationId
          val requesterName: String           = submitterName.getOrElse(throw new RuntimeException("requestedByName not found"))
          val requesterEmail: LaxEmailAddress = submitterEmail.getOrElse(throw new RuntimeException("requestedByEmailAddress not found"))

          val ticket = DeskproTicket.createForTermsOfUseUplift(requesterName, requesterEmail, appName, appId)
          deskproConnector.createTicket(riVerification.id, ticket).map(Some(_))
        }
      }
      // Do not send a deskpro ticket for a ResponsibleIndividualVerification of type ResponsibleIndividualUpdateVerification
      case _                                                => Future.successful(None)
    }
  }
}
