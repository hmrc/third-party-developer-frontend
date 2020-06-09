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

import domain._
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import service.ApplicationService
import uk.gov.hmrc.http.HeaderCarrier
import org.mockito.BDDMockito.given

import java.util.UUID
import java.util.UUID.randomUUID

import scala.concurrent.Future.{failed, successful}
import connectors.AbstractSubscriptionFieldsConnector
import scala.concurrent.Future
import domain.ApiSubscriptionFields.SubscriptionFieldValue

trait SubscriptionFieldsConnectorMock extends MockitoSugar {
  val mockSubscriptionFieldsConnector = mock[AbstractSubscriptionFieldsConnector]

  def fetchFieldValuesReturns(clientId: String, context:String, version: String)(toReturn: Seq[SubscriptionFieldValue]) : Unit =
     given(
        mockSubscriptionFieldsConnector.fetchFieldValues(
          eqTo(clientId),
          eqTo(context),
          eqTo(version))(any[HeaderCarrier]))
     .willReturn(Future.successful(toReturn))
}
