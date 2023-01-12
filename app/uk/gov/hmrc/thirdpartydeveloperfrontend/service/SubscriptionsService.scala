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

package uk.gov.hmrc.thirdpartydeveloperfrontend.service

import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.DeskproConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.ApiVersion
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.{Application, ApplicationId}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.{DeskproTicket, TicketResult}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.DeveloperSession
import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.ApplicationUpdateSuccessful
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.FieldName
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ApmConnector
import scala.concurrent.ExecutionContext

@Singleton
class SubscriptionsService @Inject() (
    deskproConnector: DeskproConnector,
    apmConnector: ApmConnector,
    subscriptionFieldsService: SubscriptionFieldsService,
    auditService: AuditService
  )(implicit ec: ExecutionContext
  ) {

  private def doRequest(
      requester: DeveloperSession,
      application: Application,
      apiName: String,
      apiVersion: ApiVersion
    )(
      f: (String, String, String, ApplicationId, String, ApiVersion) => DeskproTicket
    ) = {
    f(requester.displayedName, requester.email, application.name, application.id, apiName, apiVersion)
  }

  def requestApiSubscription(requester: DeveloperSession, application: Application, apiName: String, apiVersion: ApiVersion)(implicit hc: HeaderCarrier): Future[TicketResult] = {
    deskproConnector.createTicket(doRequest(requester, application, apiName, apiVersion)(DeskproTicket.createForApiSubscribe))
  }

  def requestApiUnsubscribe(requester: DeveloperSession, application: Application, apiName: String, apiVersion: ApiVersion)(implicit hc: HeaderCarrier): Future[TicketResult] = {
    deskproConnector.createTicket(doRequest(requester, application, apiName, apiVersion)(DeskproTicket.createForApiUnsubscribe))
  }

  type ApiMap[V]   = Map[ApiContext, Map[ApiVersion, V]]
  type FieldMap[V] = ApiMap[Map[FieldName, V]]

  def subscribeToApi(application: Application, apiIdentifier: ApiIdentifier)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] = {
    apmConnector.subscribeToApi(application.id, apiIdentifier)
  }

  def isSubscribedToApi(applicationId: ApplicationId, apiIdentifier: ApiIdentifier)(implicit hc: HeaderCarrier): Future[Boolean] = {
    for {
      app <- apmConnector.fetchApplicationById(applicationId)
      subs = app.map(_.subscriptions).getOrElse(Set.empty)
    } yield subs.contains(apiIdentifier)
  }

}

object SubscriptionsService {

  trait SubscriptionsConnector {
    def subscribeToApi(applicationId: ApplicationId, apiIdentifier: ApiIdentifier)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful]
  }
}
