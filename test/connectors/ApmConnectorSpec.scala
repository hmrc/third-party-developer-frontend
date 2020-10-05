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

package connectors

import model.APICategoryDetails
import domain.models.connectors.ExtendedApiDefinition
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import utils.AsyncHmrcSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ApmConnectorSpec extends AsyncHmrcSpec with BeforeAndAfterEach with GuiceOneAppPerSuite {

  trait Setup {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val serviceBaseUrl = "http://api-platform-microservice"
    val mockHttpClient: HttpClient = mock[HttpClient]

    val config: ApmConnector.Config = ApmConnector.Config(serviceBaseUrl)

    val connectorUnderTest: ApmConnector = new ApmConnector(mockHttpClient, config)
  }

  "fetchAllAPICategories" should {
    val category1 = APICategoryDetails("CATEGORY_1", "Category 1")
    val category2 = APICategoryDetails("CATEGORY_2", "Category 2")

    "return all API Category details" in new Setup {
      when(mockHttpClient.GET[Seq[APICategoryDetails]](eqTo(s"$serviceBaseUrl/api-categories"))(*, *, *))
        .thenReturn(Future.successful(Seq(category1, category2)))

      val result = await(connectorUnderTest.fetchAllAPICategories())

      result.size should be (2)
      result should contain only (category1, category2)
    }
  }

   "fetchApiDefinitionsVisibleToUser" should {

    "return APIs" in new Setup {
      val userEmail = "foo@bar.com"

      val mockExpectedApi = mock[ExtendedApiDefinition]
        when(mockHttpClient.GET[Seq[ExtendedApiDefinition]](eqTo(s"$serviceBaseUrl/combined-api-definitions"), eqTo(Seq("collaboratorEmail" -> userEmail)))(*, *, *))
        .thenReturn(Future.successful(Seq(mockExpectedApi)))

      val result = await(connectorUnderTest.fetchApiDefinitionsVisibleToUser(userEmail))

      result.size should be (1)
      result should contain only (mockExpectedApi)
    }


   }

  
}
