/*
 * Copyright 2022 HM Revenue & Customs
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
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.DeskproTicket
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.DeskproConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.ApplicationService
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models.{ErrorDetails, ResponsibleIndividualVerification, ResponsibleIndividualVerificationWithDetails}
import uk.gov.hmrc.apiplatform.modules.common.services.EitherTHelper
import uk.gov.hmrc.apiplatform.modules.submissions.connectors.ThirdPartyApplicationSubmissionsConnector
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models.ResponsibleIndividualToUVerification
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.ApplicationWithSubscriptionData

@Singleton
class ResponsibleIndividualVerificationService @Inject()(
  tpaSubmissionsConnector: ThirdPartyApplicationSubmissionsConnector,
  applicationService: ApplicationService,
  deskproConnector: DeskproConnector
)(
  implicit val ec: ExecutionContext
) {

  private val ET = EitherTHelper.make[ErrorDetails]

  def fetchResponsibleIndividualVerification(code: String)(implicit hc: HeaderCarrier): Future[Option[ResponsibleIndividualVerification]] = {
    tpaSubmissionsConnector.fetchResponsibleIndividualVerification(code)
  }

  def verifyResponsibleIndividual(code: String, verified: Boolean)(implicit hc: HeaderCarrier): Future[Either[ErrorDetails, ResponsibleIndividualVerification]] = {
    verified match {
      case true => // Responsible individual has accepted
        (
          for {
            riVerification <- ET.fromOptionF(tpaSubmissionsConnector.fetchResponsibleIndividualVerification(code), ErrorDetails("riverification001", s"No responsibleIndividualVerification record found for ${code}")) 
            application    <- ET.fromOptionF(applicationService.fetchByApplicationId(riVerification.applicationId), ErrorDetails("riverification002", s"No application record found for ${riVerification.applicationId}"))
            submitterEmail <- ET.fromOption(application.application.state.requestedByEmailAddress, ErrorDetails("riverification003", "requestedByEmailAddress not found"))
            submitterName  <- ET.fromOption(application.application.state.requestedByName, ErrorDetails("riverification004", "requestedByName not found"))
            _              <- ET.liftF(applicationService.acceptResponsibleIndividualVerification(riVerification.applicationId, code))
            _              =  sendDeskproTicketForTermsOfUse(riVerification, submitterName, submitterEmail)
          } yield riVerification
        )
        .value
      case false => // Responsible individual has declined
        (
          for {
            riVerification <- ET.fromEitherF(tpaSubmissionsConnector.responsibleIndividualDecline(code))
          } yield riVerification
        )
        .value
    }
  }

  private def sendDeskproTicketForTermsOfUse(riVerification: ResponsibleIndividualVerification, submitterName: String, submitterEmail: String)(implicit hc: HeaderCarrier) = {
 
    riVerification match {
      // Only send deskpro ticket for a ResponsibleIndividualVerification of type 'terms of use'
      case riv: ResponsibleIndividualToUVerification => { 
        val appName = riVerification.applicationName
        val appId = riVerification.applicationId

        val ticket = DeskproTicket.createForRequestProductionCredentials(submitterName, submitterEmail, appName, appId)
        deskproConnector.createTicket(ticket)
      }
      case _ =>
    }
  }
}