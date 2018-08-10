/*
 * Copyright 2018 HM Revenue & Customs
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
import domain.{Application, ApplicationNotFound}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait SubscriptionFieldsService {

  protected val connectorsWrapper: ConnectorsWrapper

  def fetchFields(application: Application, apiContext: String, apiVersion: String)(implicit hc: HeaderCarrier): Future[Seq[SubscriptionField]] = {

    val connector = connectorsWrapper.connectorsForEnvironment(application.deployedTo).apiSubscriptionFieldsConnector

    def addValuesToDefinitions(defs: Seq[SubscriptionField], fieldValues: Fields) = {
      defs.map(field => field.withValue(fieldValues.get(field.name)))
    }

    def fetchFieldsValues(defs: Seq[SubscriptionField])(implicit hc: HeaderCarrier): Future[Seq[SubscriptionField]] = {
      for {
        maybeValues <- connector.fetchFieldValues(application.clientId, apiContext, apiVersion)
      } yield maybeValues.fold(defs) { response =>
        addValuesToDefinitions(defs, response.fields)
      }
    }

    connector.fetchFieldDefinitions(apiContext, apiVersion).flatMap(fetchFieldsValues)
  }

  def saveFieldValues(applicationId: String, apiContext: String, apiVersion: String, fields: Fields)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    for {
      connector <- connectorsWrapper.forApplication(applicationId)
      app <- connector.thirdPartyApplicationConnector.fetchApplicationById(applicationId)
      fields <- connector.apiSubscriptionFieldsConnector.saveFieldValues(app.getOrElse(throw new ApplicationNotFound).clientId, apiContext, apiVersion, fields)
    } yield fields
  }
}

object SubscriptionFieldsService extends SubscriptionFieldsService {
  override protected val connectorsWrapper: ConnectorsWrapper = ConnectorsWrapper
}

