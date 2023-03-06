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

package uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.connectors

import scala.concurrent.Future

import org.mockito.{ArgumentMatchersSugar, MockitoSugar}

import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.AbstractSubscriptionFieldsConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.{ApiContext, ApiVersion}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.ApiSubscriptionFields.SubscriptionFieldValue
import uk.gov.hmrc.apiplatform.modules.applications.domain.models.ClientId

import uk.gov.hmrc.apiplatform.modules.applications.domain.models.ClientId
trait SubscriptionFieldsConnectorMock extends MockitoSugar with ArgumentMatchersSugar {
  val mockSubscriptionFieldsConnector = mock[AbstractSubscriptionFieldsConnector]

  def fetchFieldValuesReturns(clientId: ClientId, context: ApiContext, version: ApiVersion)(toReturn: Seq[SubscriptionFieldValue]): Unit =
    when(mockSubscriptionFieldsConnector.fetchFieldValues(eqTo(clientId), eqTo(context), eqTo(version))(*))
      .thenReturn(Future.successful(toReturn))
}
