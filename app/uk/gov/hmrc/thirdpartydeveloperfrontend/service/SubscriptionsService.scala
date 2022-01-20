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

package uk.gov.hmrc.thirdpartydeveloperfrontend.service

import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.DeskproConnector
import domain.models.apidefinitions.ApiVersion
import domain.models.applications.{Application, ApplicationId}
import domain.models.connectors.{DeskproTicket, TicketResult}
import domain.models.developers.DeveloperSession
import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future
import domain.models.apidefinitions._
import domain.ApplicationUpdateSuccessful
import domain.models.subscriptions.FieldName
import domain.models.subscriptions.ApiSubscriptionFields.SubscriptionFieldDefinition
import domain.models.subscriptions.ApiSubscriptionFields.SaveSubscriptionFieldsSuccessResponse
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ApmConnector
import scala.concurrent.ExecutionContext

@Singleton
class SubscriptionsService @Inject() (
  deskproConnector: DeskproConnector,
  apmConnector: ApmConnector,
  subscriptionFieldsService: SubscriptionFieldsService,
  auditService: AuditService
)(implicit ec: ExecutionContext) {

  private def doRequest(requester: DeveloperSession, application: Application, apiName: String, apiVersion: ApiVersion)(
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

  type ApiMap[V] = Map[ApiContext, Map[ApiVersion, V]]
  type FieldMap[V] = ApiMap[Map[FieldName,V]]

  def subscribeToApi(application: Application, apiIdentifier: ApiIdentifier)(implicit hc: HeaderCarrier): Future[ApplicationUpdateSuccessful] = {

    def ensureEmptyValuesWhenNoneExists(fieldDefinitions: Seq[SubscriptionFieldDefinition]): Future[Unit] = {
      for {
        oldValues <- subscriptionFieldsService.fetchFieldsValues(application, fieldDefinitions, apiIdentifier)
        saveResponse <- subscriptionFieldsService.saveBlankFieldValues(application, apiIdentifier.context, apiIdentifier.version, oldValues)
      } yield saveResponse match {
        case SaveSubscriptionFieldsSuccessResponse => ()
        case error =>
          val errorMessage = s"Failed to save blank subscription field values: $error"
          throw new RuntimeException(errorMessage)
      }
    }

    def ensureSavedValuesForAnyDefinitions(defns: Seq[SubscriptionFieldDefinition]): Future[Unit] = {
      if (defns.nonEmpty) {
        ensureEmptyValuesWhenNoneExists(defns)
      } else {
        Future.successful(())
      }
    }

    val subscribeResponse: Future[ApplicationUpdateSuccessful] = apmConnector.subscribeToApi(application.id, apiIdentifier)
    
    val fieldDefinitions: Future[Seq[SubscriptionFieldDefinition]] = subscriptionFieldsService.getFieldDefinitions(application, apiIdentifier)

    fieldDefinitions
      .flatMap(ensureSavedValuesForAnyDefinitions)
      .flatMap(_ => subscribeResponse)
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

