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
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.ApplicationId
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.DeveloperSession
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.DeskproTicket
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.DeskproConnector
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Application
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models.ErrorDetails
import uk.gov.hmrc.apiplatform.modules.common.services.EitherTHelper
import uk.gov.hmrc.apiplatform.modules.submissions.connectors.ThirdPartyApplicationSubmissionsConnector
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models.ResponsibleIndividualVerification

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
    (
      for {
        riVerification <- ET.fromOptionF(tpaConnector.fetchResponsibleIndividualVerification(code), ErrorDetails("RecordNotFound", "Error - can't find responsibleIndividualVerification record"))
        ticket         = DeskproTicket.createForRequestProductionCredentials("requestedBy.displayedName", "requestedBy.email", riVerification.appName, riVerification.appId)
        _              = deskproConnector.createTicket(ticket)
      } yield riVerification
    )
    .value
  }
}