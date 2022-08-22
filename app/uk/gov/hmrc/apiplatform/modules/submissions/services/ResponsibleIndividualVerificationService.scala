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
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models.{ErrorDetails, ResponsibleIndividualVerification, ResponsibleIndividualVerificationWithDetails}
import uk.gov.hmrc.apiplatform.modules.common.services.EitherTHelper
import uk.gov.hmrc.apiplatform.modules.submissions.connectors.ThirdPartyApplicationSubmissionsConnector
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models.ResponsibleIndividualToUVerification

@Singleton
class ResponsibleIndividualVerificationService @Inject()(
  tpaConnector: ThirdPartyApplicationSubmissionsConnector,
  deskproConnector: DeskproConnector
)(
  implicit val ec: ExecutionContext
) {

  private val ET = EitherTHelper.make[ErrorDetails]

  def fetchResponsibleIndividualVerification(code: String)(implicit hc: HeaderCarrier): Future[Option[ResponsibleIndividualVerification]] = {
    tpaConnector.fetchResponsibleIndividualVerification(code)
  }

  def verifyResponsibleIndividual(code: String, verified: Boolean)(implicit hc: HeaderCarrier): Future[Either[ErrorDetails, ResponsibleIndividualVerification]] = {
    verified match {
      case true => // Responsible individual has accepted
        (
          for {
            riVerificationWithDetails <- ET.fromEitherF(tpaConnector.responsibleIndividualAccept(code))
            riVerification             = riVerificationWithDetails.verification
            _                          = sendDeskproTicketForTermsOfUse(riVerificationWithDetails)
          } yield riVerification
        )
        .value
      case false => // Responsible individual has declined
        (
          for {
            riVerification <- ET.fromEitherF(tpaConnector.responsibleIndividualDecline(code))
          } yield riVerification
        )
        .value
    }
  }

  private def sendDeskproTicketForTermsOfUse(riVerificationWithDetails: ResponsibleIndividualVerificationWithDetails)(implicit hc: HeaderCarrier) = {
    val verification = riVerificationWithDetails.verification

    verification match {
      // Only send deskpro ticket for a ResponsibleIndividualVerification of type 'terms of use'
      case riv: ResponsibleIndividualToUVerification => { 
        val name = riVerificationWithDetails.submitterName
        val email = riVerificationWithDetails.submitterEmail
        val appName = riv.applicationName
        val appId = riv.applicationId

        val ticket = DeskproTicket.createForRequestProductionCredentials(name, email, appName, appId)
        deskproConnector.createTicket(ticket)
      }
      case _ =>
    }
  }
}