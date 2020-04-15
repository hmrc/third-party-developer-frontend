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

import domain.{APIIdentifier, Application, ApplicationNotFound, Environment}
import domain.ApiSubscriptionFields._
import javax.inject.{Inject, Singleton}
import service.SubscriptionFieldsService.DefinitionsByApiVersion
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SubscriptionFieldsService @Inject()(connectorsWrapper: ConnectorsWrapper)(implicit val ec: ExecutionContext) {

  def fetchFieldsValues(application: Application, fieldDefinitions: Seq[SubscriptionFieldDefinition], apiIdentifier: APIIdentifier)
                       (implicit hc: HeaderCarrier): Future[Seq[SubscriptionFieldValue]] = {
    val connector = connectorsWrapper.connectorsForEnvironment(application.deployedTo).apiSubscriptionFieldsConnector

    if (fieldDefinitions.isEmpty) {
      Future.successful(Seq.empty[SubscriptionFieldValue])
    } else {
      connector.fetchFieldValues(application.clientId, apiIdentifier.context, apiIdentifier.version)
    }
  }

  def saveFieldValues(applicationId: String, apiContext: String, apiVersion: String, fields: Fields)
                     (implicit hc: HeaderCarrier): Future[SaveSubscriptionFieldsResponse] = {
    for {
      connector <- connectorsWrapper.forApplication(applicationId)
      application <- connector.thirdPartyApplicationConnector.fetchApplicationById(applicationId)
      fields <- connector.apiSubscriptionFieldsConnector.saveFieldValues(
        application.getOrElse(throw new ApplicationNotFound).clientId, apiContext, apiVersion, fields
      )
    } yield fields
  }

  def getAllFieldDefinitions(environment: Environment)(implicit hc: HeaderCarrier): Future[DefinitionsByApiVersion] = {
    connectorsWrapper
      .connectorsForEnvironment(environment)
      .apiSubscriptionFieldsConnector.fetchAllFieldDefinitions()
  }

  def getFieldDefinitions(application: Application, apiIdentifier: APIIdentifier)(implicit hc: HeaderCarrier): Future[Seq[SubscriptionFieldDefinition]] = {
    val connector = connectorsWrapper.connectorsForEnvironment(application.deployedTo).apiSubscriptionFieldsConnector

    connector.fetchFieldDefinitions(apiIdentifier.context, apiIdentifier.version)
  }
}

object SubscriptionFieldsService {
  trait SubscriptionFieldsConnector {
    def fetchFieldValues(clientId: String, context: String, version: String)
                        (implicit hc: HeaderCarrier) : Future[Seq[SubscriptionFieldValue]]

    def fetchFieldsValuesWithPrefetchedDefinitions(clientId: String, apiIdentifier: APIIdentifier, definitionsCache: DefinitionsByApiVersion)
                                                  (implicit hc: HeaderCarrier): Future[Seq[SubscriptionFieldValue]]

    def fetchAllFieldDefinitions()(implicit hc: HeaderCarrier): Future[DefinitionsByApiVersion]

    def fetchFieldDefinitions(apiContext: String, apiVersion: String)
                             (implicit hc: HeaderCarrier): Future[Seq[SubscriptionFieldDefinition]]

    def saveFieldValues(clientId: String, apiContext: String, apiVersion: String, fields: Fields)
                       (implicit hc: HeaderCarrier): Future[SaveSubscriptionFieldsResponse]

    def deleteFieldValues(clientId: String, apiContext: String, apiVersion: String)(implicit hc: HeaderCarrier): Future[FieldsDeleteResult]
  }

  type DefinitionsByApiVersion = Map[APIIdentifier, Seq[SubscriptionFieldDefinition]]

  object DefinitionsByApiVersion {
    val empty = Map.empty[APIIdentifier, Seq[SubscriptionFieldDefinition]]
  }
}
