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
}
