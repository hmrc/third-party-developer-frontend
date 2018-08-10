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

package unit.connectors

import java.util.UUID

import com.github.tomakehurst.wiremock.client.WireMock._
import config.WSHttp
import connectors.ApiSubscriptionFieldsConnector
import domain.ApiSubscriptionFields._
import play.api.http.Status._
import play.api.libs.json.Json
import uk.gov.hmrc.http.{HeaderCarrier, JsValidationException, NotFoundException, Upstream5xxResponse}

class ApiSubscriptionFieldsConnectorSpec extends BaseConnectorSpec {

  implicit val hc = HeaderCarrier()
  val clientId: String = UUID.randomUUID().toString
  val apiContext: String = "i-am-a-test"
  val apiVersion: String = "1.0"
  val fieldsId = UUID.randomUUID()
  val urlPrefix = "/field"

  trait Setup {
    val underTest = new ApiSubscriptionFieldsConnector {
      override val serviceBaseUrl: String = wireMockUrl
      override val http = WSHttp
    }
  }


  "fetchFieldValues" should {
    val response = SubscriptionFields(clientId, apiContext, apiVersion, fieldsId, fields("field001" -> "field002"))
    val getUrl = s"$urlPrefix/application/$clientId/context/$apiContext/version/$apiVersion"

    "return subscription fields for an API" in new Setup {

      stubFor(get(urlPathMatching(getUrl))
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withHeader("Content-Type", "application/json")
            .withBody(Json.toJson(response).toString())))

      val result: Option[SubscriptionFields] = await(underTest.fetchFieldValues(clientId, apiContext, apiVersion))

      result shouldBe Some(response)
    }

    "fail when api-subscription-fields returns a 500" in new Setup {

      stubFor(get(urlPathMatching(getUrl))
        .willReturn(
          aResponse()
            .withStatus(INTERNAL_SERVER_ERROR)
            .withHeader("Content-Type", "application/json")))

      intercept[Upstream5xxResponse] {
        await(underTest.fetchFieldValues(clientId, apiContext, apiVersion))
      }
    }

    "return None when api-subscription-fields returns a 404" in new Setup {

      stubFor(get(urlPathMatching(getUrl))
        .willReturn(
          aResponse()
            .withStatus(NOT_FOUND)))

      val result: Option[SubscriptionFields] = await(underTest.fetchFieldValues(clientId, apiContext, apiVersion))
      result shouldBe None
    }

  }

  "fetchFieldDefinitions" should {

    val fields = List(SubscriptionField("field1", "desc1", "hint1", "some type"), SubscriptionField("field2", "desc2", "hint2", "some other type"))
    val invalidResponse = Map("whatever" -> fields)
    val validResponse = Map("fieldDefinitions" -> fields)
    val url = s"/definition/context/$apiContext/version/$apiVersion"

    "return subscription fields definition for an API" in new Setup {

      stubFor(get(urlPathMatching(url))
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withHeader("Content-Type", "application/json")
            .withBody(Json.toJson(validResponse).toString())))

      val result: Seq[SubscriptionField] = await(underTest.fetchFieldDefinitions(apiContext, apiVersion))

      result shouldBe fields
    }

    "fail when api-subscription-fields returns a 500" in new Setup {

      stubFor(get(urlPathMatching(url))
        .willReturn(
          aResponse()
            .withStatus(INTERNAL_SERVER_ERROR)
            .withHeader("Content-Type", "application/json")))

      intercept[Upstream5xxResponse] {
        await(underTest.fetchFieldDefinitions(apiContext, apiVersion))
      }
    }

    "return empty sequence when api-subscription-fields returns a 404" in new Setup {

      stubFor(get(urlPathMatching(url))
        .willReturn(
          aResponse()
            .withStatus(NOT_FOUND)))

      val result: Seq[SubscriptionField] = await(underTest.fetchFieldDefinitions(apiContext, apiVersion))
      result shouldBe Seq.empty[SubscriptionField]
    }

    "fail when api-subscription-fields returns unexpected response" in new Setup {

      stubFor(get(urlPathMatching(url))
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withHeader("Content-Type", "application/json")
            .withBody(Json.toJson(invalidResponse).toString())))

      intercept[JsValidationException] {
        await(underTest.fetchFieldDefinitions(apiContext, apiVersion))
      }
    }
  }

  "saveFieldValues" should {

    val fieldsValues = fields("field001" -> "value001", "field002" -> "value002")
    val subFieldsPutRequest = SubscriptionFieldsPutRequest(clientId, apiContext, apiVersion, fieldsValues)

    val putUrl = s"$urlPrefix/application/$clientId/context/$apiContext/version/$apiVersion"

    "save the fields" in new Setup {
      stubFor(put(urlPathMatching(putUrl))
        .willReturn(
          aResponse()
            .withStatus(OK)))

      await(underTest.saveFieldValues(clientId, apiContext, apiVersion, fieldsValues))

      verify(putRequestedFor(urlPathMatching(putUrl)).withRequestBody(equalToJson(Json.toJson(subFieldsPutRequest).toString())))
    }

    "fail when api-subscription-fields returns a 500" in new Setup {

      stubFor(put(urlPathMatching(putUrl))
        .willReturn(
          aResponse()
            .withStatus(INTERNAL_SERVER_ERROR)
            .withHeader("Content-Type", "application/json")))

      intercept[Upstream5xxResponse] {
        await(underTest.saveFieldValues(clientId, apiContext, apiVersion, fieldsValues))
      }
    }

    "fail when api-subscription-fields returns a 404" in new Setup {

      stubFor(put(urlPathMatching(putUrl))
        .willReturn(
          aResponse()
            .withStatus(NOT_FOUND)))

      intercept[NotFoundException] {
        await(underTest.saveFieldValues(clientId, apiContext, apiVersion, fieldsValues))
      }
    }
  }

  "deleteFieldValues" should {

    val url = s"$urlPrefix/application/$clientId/context/$apiContext/version/$apiVersion"

    "return true after delete call has returned 204" in new Setup {
      stubFor(delete(urlPathMatching(url))
        .willReturn(
          aResponse()
            .withStatus(NO_CONTENT)))

      val result = await(underTest.deleteFieldValues(clientId, apiContext, apiVersion))

      result shouldBe true
    }

    "return false api-subscription-fields returns unexpected status" in new Setup {
      stubFor(delete(urlPathMatching(url))
        .willReturn(
          aResponse()
            .withStatus(ACCEPTED)))

      val result = await(underTest.deleteFieldValues(clientId, apiContext, apiVersion))

      result shouldBe false
    }

    "fail when api-subscription-fields returns a 500" in new Setup {

      stubFor(delete(urlPathMatching(url))
        .willReturn(
          aResponse()
            .withStatus(INTERNAL_SERVER_ERROR)))

      intercept[Upstream5xxResponse] {
        await(underTest.deleteFieldValues(clientId, apiContext, apiVersion))
      }
    }

    "return true when api-subscription-fields returns a 404" in new Setup {

      stubFor(delete(urlPathMatching(url))
        .willReturn(
          aResponse()
            .withStatus(NOT_FOUND)))

      val result = await(underTest.deleteFieldValues(clientId, apiContext, apiVersion))
      result shouldBe true
    }

  }
}
