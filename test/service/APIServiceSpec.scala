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

import utils.AsyncHmrcSpec
import connectors.ApmConnector
import domain.models.connectors.ExtendedApiDefinition

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import uk.gov.hmrc.http.HeaderCarrier
import model.APICategoryDetails

class APIServiceSpec extends AsyncHmrcSpec {
  
    trait Setup {
        implicit val hc: HeaderCarrier = HeaderCarrier()

        val mockAPMConnector = mock[ApmConnector]
        val serviceUnderTest: APIService = new APIService(mockAPMConnector)
    }

    "fetchAllAPICategoryDetails" should {
        val category1 = mock[APICategoryDetails]
        val category2 = mock[APICategoryDetails]

        "return all APICategoryDetails objects from connector" in new Setup {
            when(mockAPMConnector.fetchAllAPICategories()(*)).thenReturn(Future.successful(Seq(category1, category2)))

            val result = await(serviceUnderTest.fetchAllAPICategoryDetails())

            result.size should be (2)
            result should contain only (category1, category2)

            verify(mockAPMConnector).fetchAllAPICategories()(*)
        }
    }

    "fetchAPIDetails" should {
        val apiServiceName1 = "service-1"
        val apiServiceName2 = "service-2"

        val apiDetails1 = mock[ExtendedApiDefinition]
        val apiDetails2 = mock[ExtendedApiDefinition]

        "return details of APIs by serviceName" in new Setup {
            when(mockAPMConnector.fetchAPIDefinition(eqTo(apiServiceName1))(*)).thenReturn(Future.successful(apiDetails1))
            when(mockAPMConnector.fetchAPIDefinition(eqTo(apiServiceName2))(*)).thenReturn(Future.successful(apiDetails2))

            val result = await(serviceUnderTest.fetchAPIDetails(Set(apiServiceName1, apiServiceName2)))

            result.size should be (2)
            result should contain only (apiDetails1, apiDetails2)

            verify(mockAPMConnector).fetchAPIDefinition(eqTo(apiServiceName1))(*)
            verify(mockAPMConnector).fetchAPIDefinition(eqTo(apiServiceName2))(*)
        }
    }
}
