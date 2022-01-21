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

@Singleton
class RequestProductionCredentials @Inject()(
  tpaConnector: ThirdPartyApplicationSubmissionsConnector,
  deskproConnector: DeskproConnector
)(
  implicit val ec: ExecutionContext
) {

  private val ET = EitherTHelper.make[ErrorDetails]

  def requestProductionCredentials(applicationId: ApplicationId, requestedBy: DeveloperSession)(implicit hc: HeaderCarrier): Future[Either[ErrorDetails, Application]] = {
    (
      for {
        app           <- ET.fromEitherF(tpaConnector.requestApproval(applicationId, requestedBy.email))
        upliftTicket   = DeskproTicket.createForUplift(requestedBy.displayedName, requestedBy.email, app.name, applicationId)
        _              = deskproConnector.createTicket(upliftTicket)
      } yield app
    )
    .value
  }
}