/*
 * Copyright 2021 HM Revenue & Customs
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

package modules.submissions.services

import javax.inject.{Inject, Singleton}
import domain.models.applications.ApplicationId
import domain.models.developers.DeveloperSession
import uk.gov.hmrc.http.HeaderCarrier
import domain.models.connectors.DeskproTicket
import domain.models.applications.UpliftRequest
import service.ConnectorsWrapper
import connectors.DeskproConnector
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import domain.models.HasSuceeded


@Singleton
class RequestProductionCredentials @Inject()(
  connectorWrapper: ConnectorsWrapper,
  deskproConnector: DeskproConnector
)(
  implicit val ec: ExecutionContext
) {
  def requestProductionCredentials(applicationId: ApplicationId, applicationName: String, requestedBy: DeveloperSession)(implicit hc: HeaderCarrier): Future[HasSuceeded] = {
    for {
      _ <- connectorWrapper.productionApplicationConnector.requestApproval(applicationId, UpliftRequest(applicationName, requestedBy.email))
      upliftTicket = DeskproTicket.createForUplift(requestedBy.displayedName, requestedBy.email, applicationName, applicationId)
      _ = deskproConnector.createTicket(upliftTicket)
    } yield HasSuceeded
  }
}