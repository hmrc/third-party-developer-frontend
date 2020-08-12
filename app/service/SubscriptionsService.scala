/*
 * Copyright 2020 HM Revenue & Customs
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

package service

import connectors.DeskproConnector
import domain.models.applications.Application
import domain.models.connectors.{DeskproTicket, TicketResult}
import domain.models.developers.DeveloperSession
import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

@Singleton
class SubscriptionsService @Inject()(deskproConnector: DeskproConnector,
                                     auditService: AuditService) {

  private def doRequest(requester: DeveloperSession, application: Application, apiName: String, apiVersion: String)
                       (f: (String, String, String, String, String, String) => DeskproTicket)
                       (implicit hc: HeaderCarrier) = {
    f(requester.displayedName, requester.email, application.name, application.id, apiName, apiVersion)
  }

  def requestApiSubscription(requester: DeveloperSession,
                             application: Application,
                             apiName: String,
                             apiVersion: String)(implicit hc: HeaderCarrier): Future[TicketResult] = {
    deskproConnector.createTicket(doRequest(requester, application, apiName, apiVersion)(DeskproTicket.createForApiSubscribe))
  }

  def requestApiUnsubscribe(requester: DeveloperSession,
                            application: Application,
                            apiName: String,
                            apiVersion: String)(implicit hc: HeaderCarrier): Future[TicketResult] = {
    deskproConnector.createTicket(doRequest(requester, application, apiName, apiVersion)(DeskproTicket.createForApiUnsubscribe))
  }
}
