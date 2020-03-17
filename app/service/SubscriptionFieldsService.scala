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

import domain.ApiSubscriptionFields._
import domain.{APIIdentifier, Application, ApplicationNotFound, Environment}
import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SubscriptionFieldsService @Inject()(connectorsWrapper: ConnectorsWrapper)(implicit val ec: ExecutionContext) {
  def fetchFieldsValues(application: Application, fieldDefinitions: Seq[SubscriptionField], apiIdentifier: APIIdentifier)(implicit hc: HeaderCarrier): Future[Seq[SubscriptionField]] = {
    val connector = connectorsWrapper.connectorsForEnvironment(application.deployedTo).apiSubscriptionFieldsConnector

    def addValuesToDefinitions(defs: Seq[SubscriptionField], fieldValues: Fields) = {
      defs.map(field => field.withValue(fieldValues.get(field.name)))
    }

    if (fieldDefinitions.isEmpty)
      Future.successful(Seq.empty)
    else {
      for {
        maybeValues <- connector.fetchFieldValues(application.clientId, apiIdentifier.context, apiIdentifier.version)
      } yield maybeValues.fold(fieldDefinitions) { response =>
        addValuesToDefinitions(fieldDefinitions, response.fields)
      }
    }
  }

  def saveFieldValues(applicationId: String, apiContext: String, apiVersion: String, fields: Fields)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    for {
      connector <- connectorsWrapper.forApplication(applicationId)
      application <- connector.thirdPartyApplicationConnector.fetchApplicationById(applicationId)
      fields <- connector.apiSubscriptionFieldsConnector.saveFieldValues(application.getOrElse(throw new ApplicationNotFound).clientId, apiContext, apiVersion, fields)
    } yield fields
  }

  def getAllFieldDefinitions(environment: Environment)(implicit hc: HeaderCarrier): Future[Map[APIIdentifier, FieldDefinitions]] = {
    def toMap(definitions : Seq[FieldDefinitions]): Map[APIIdentifier, FieldDefinitions] = {
      definitions.map(definition => APIIdentifier(definition.apiContext, definition.apiVersion) -> definition).toMap
    }

    val apiSubscriptionFieldsConnector = connectorsWrapper.connectorsForEnvironment(environment).apiSubscriptionFieldsConnector

    for {
      allFieldDefinitions <- apiSubscriptionFieldsConnector.fetchAllFieldDefinitions()
    } yield toMap(allFieldDefinitions)
  }

  def getFieldDefinitions(application: Application, apiIdentifier: APIIdentifier)(implicit hc: HeaderCarrier): Future[Seq[SubscriptionField]] = {
    val connector = connectorsWrapper.connectorsForEnvironment(application.deployedTo).apiSubscriptionFieldsConnector

    connector.fetchFieldDefinitions(apiIdentifier.context, apiIdentifier.version)
  }
}
