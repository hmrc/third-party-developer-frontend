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

package mocks.connector

import connectors.AbstractSubscriptionFieldsConnector
import domain.models.subscriptions.ApiSubscriptionFields.SubscriptionFieldValue
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future
import domain.models.apidefinitions.{ApiContext, ApiVersion}
import domain.models.applications.ClientId

trait SubscriptionFieldsConnectorMock extends MockitoSugar with ArgumentMatchersSugar {
  val mockSubscriptionFieldsConnector = mock[AbstractSubscriptionFieldsConnector]

  def fetchFieldValuesReturns(clientId: ClientId, context: ApiContext, version: ApiVersion)(toReturn: Seq[SubscriptionFieldValue]): Unit =
    when(mockSubscriptionFieldsConnector.fetchFieldValues(eqTo(clientId), eqTo(context), eqTo(version))(any[HeaderCarrier]))
      .thenReturn(Future.successful(toReturn))
}
