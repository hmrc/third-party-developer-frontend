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
import connectors.DeskproConnector
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import domain.models.HasSuceeded
import connectors.ThirdPartyApplicationProductionConnector

@Singleton
class RequestProductionCredentials @Inject()(
  productionApplicationConnector: ThirdPartyApplicationProductionConnector,
  deskproConnector: DeskproConnector
)(
  implicit val ec: ExecutionContext
) {
  def requestProductionCredentials(applicationId: ApplicationId, requestedBy: DeveloperSession)(implicit hc: HeaderCarrier): Future[HasSuceeded] = {
    for {
      app           <- productionApplicationConnector.requestApproval(applicationId, requestedBy.email)
      upliftTicket   = DeskproTicket.createForUplift(requestedBy.displayedName, requestedBy.email, app.name, applicationId)
      _              = deskproConnector.createTicket(upliftTicket)
    } yield HasSuceeded
  }
}