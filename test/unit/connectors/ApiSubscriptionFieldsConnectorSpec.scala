/*
 * Copyright 2019 HM Revenue & Customs
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

import connectors.{ApiSubscriptionFieldsConnector, ProxiedHttpClient}
import domain.ApiSubscriptionFields._
import domain.Environment
import javax.inject.Singleton
import org.mockito.Matchers.{any, eq => meq}
import org.mockito.Mockito.{verify, when}
import play.api.http.Status.{ACCEPTED, INTERNAL_SERVER_ERROR, NO_CONTENT, OK}
import uk.gov.hmrc.http.{HttpResponse, _}
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class ApiSubscriptionFieldsConnectorSpec extends BaseConnectorSpec {

  private implicit val hc: HeaderCarrier = HeaderCarrier()

  private val clientId = UUID.randomUUID().toString
  private val apiContext = "i-am-a-test"
  private val apiVersion = "1.0"
  private val fieldsId = UUID.randomUUID()
  private val urlPrefix = "/field"
  private val upstream500Response = Upstream5xxResponse("", INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR)

  @Singleton
  class ApiSubscriptionFieldsTestConnector(val httpClient: HttpClient,
                                           val proxiedHttpClient: ProxiedHttpClient)(implicit val ec: ExecutionContext)
    extends ApiSubscriptionFieldsConnector {
    val environment: Environment = Environment.SANDBOX
    val useProxy = false
    val bearerToken = ""
    val serviceBaseUrl = ""
  }

  trait Setup {
    val mockHttpClient: HttpClient = mock[HttpClient]
    val mockProxiedHttpClient: ProxiedHttpClient = mock[ProxiedHttpClient]

    val underTest = new ApiSubscriptionFieldsTestConnector(mockHttpClient, mockProxiedHttpClient)
  }

  "fetchFieldValues" should {
    val response = SubscriptionFields(clientId, apiContext, apiVersion, fieldsId, fields("field001" -> "field002"))
    val getUrl = s"$urlPrefix/application/$clientId/context/$apiContext/version/$apiVersion"

    "return subscription fields for an API" in new Setup {
      when(mockHttpClient.GET[SubscriptionFields](meq(getUrl))(any(), any(), any())).thenReturn(Future.successful(response))

      val result: Option[SubscriptionFields] = await(underTest.fetchFieldValues(clientId, apiContext, apiVersion))

      result shouldBe Some(response)
    }

    "fail when api-subscription-fields returns a 500" in new Setup {

      when(mockHttpClient.GET[SubscriptionFields](meq(getUrl))(any(), any(), any()))
        .thenReturn(Future.failed(upstream500Response))

      intercept[Upstream5xxResponse] {
        await(underTest.fetchFieldValues(clientId, apiContext, apiVersion))
      }
    }

    "return None when api-subscription-fields returns a 404" in new Setup {

      when(mockHttpClient.GET[SubscriptionFields](meq(getUrl))(any(), any(), any()))
        .thenReturn(Future.failed(new NotFoundException("")))

      val result: Option[SubscriptionFields] = await(underTest.fetchFieldValues(clientId, apiContext, apiVersion))
      result shouldBe None
    }

  }

  "fetchFieldDefinitions" should {

    val fields = List(SubscriptionField("field1", "desc1", "hint1", "some type"), SubscriptionField("field2", "desc2", "hint2", "some other type"))
    val validResponse = FieldDefinitionsResponse(fields)
    val url = s"/definition/context/$apiContext/version/$apiVersion"

    "return subscription fields definition for an API" in new Setup {

      when(mockHttpClient.GET[FieldDefinitionsResponse](meq(url))(any(), any(), any()))
        .thenReturn(Future.successful(validResponse))

      val result: Seq[SubscriptionField] = await(underTest.fetchFieldDefinitions(apiContext, apiVersion))

      result shouldBe fields
    }

    "fail when api-subscription-fields returns a 500" in new Setup {

      when(mockHttpClient.GET[FieldDefinitionsResponse](meq(url))(any(), any(), any()))
        .thenReturn(Future.failed(upstream500Response))

      intercept[Upstream5xxResponse] {
        await(underTest.fetchFieldDefinitions(apiContext, apiVersion))
      }
    }

    "return empty sequence when api-subscription-fields returns a 404" in new Setup {

      when(mockHttpClient.GET[SubscriptionFields](meq(url))(any(), any(), any()))
        .thenReturn(Future.failed(new NotFoundException("")))

      val result: Seq[SubscriptionField] = await(underTest.fetchFieldDefinitions(apiContext, apiVersion))
      result shouldBe Seq.empty[SubscriptionField]
    }

    "fail when api-subscription-fields returns unexpected response" in new Setup {

      when(mockHttpClient.GET[FieldDefinitionsResponse](meq(url))(any(), any(), any()))
        .thenReturn(Future.failed(new JsValidationException("", "", FieldDefinitionsResponse.getClass, "")))

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

      when(mockHttpClient.PUT[SubscriptionFieldsPutRequest, HttpResponse](putUrl, subFieldsPutRequest))
        .thenReturn(Future.successful(HttpResponse(OK)))

      await(underTest.saveFieldValues(clientId, apiContext, apiVersion, fieldsValues))

      verify(mockHttpClient).PUT[SubscriptionFieldsPutRequest, HttpResponse](putUrl, subFieldsPutRequest)
    }

    "fail when api-subscription-fields returns a 500" in new Setup {

      when(mockHttpClient.PUT[SubscriptionFieldsPutRequest, HttpResponse](putUrl, subFieldsPutRequest))
        .thenReturn(Future.failed(upstream500Response))

      intercept[Upstream5xxResponse] {
        await(underTest.saveFieldValues(clientId, apiContext, apiVersion, fieldsValues))
      }
    }

    "fail when api-subscription-fields returns a 404" in new Setup {

      when(mockHttpClient.PUT[SubscriptionFieldsPutRequest, HttpResponse](putUrl, subFieldsPutRequest))
        .thenReturn(Future.failed(new NotFoundException("")))

      intercept[NotFoundException] {
        await(underTest.saveFieldValues(clientId, apiContext, apiVersion, fieldsValues))
      }
    }
  }

  "deleteFieldValues" should {

    val url = s"$urlPrefix/application/$clientId/context/$apiContext/version/$apiVersion"

    "return true after delete call has returned 204 NO CONTENT" in new Setup {

      when(mockHttpClient.DELETE(url))
        .thenReturn(Future.successful(HttpResponse(NO_CONTENT)))

      private val result = await(underTest.deleteFieldValues(clientId, apiContext, apiVersion))

      result shouldBe true
    }

    "return false if api-subscription-fields returns unexpected status" in new Setup {

      when(mockHttpClient.DELETE(url))
        .thenReturn(Future.successful(HttpResponse(ACCEPTED)))

      private val result = await(underTest.deleteFieldValues(clientId, apiContext, apiVersion))

      result shouldBe false
    }

    "fail when api-subscription-fields returns a 500" in new Setup {

      when(mockHttpClient.DELETE(url))
        .thenReturn(Future.failed(upstream500Response))

      intercept[Upstream5xxResponse] {
        await(underTest.deleteFieldValues(clientId, apiContext, apiVersion))
      }
    }

    "return true when api-subscription-fields returns a 404" in new Setup {

      when(mockHttpClient.DELETE(url))
        .thenReturn(Future.failed(new NotFoundException("")))

      private val result = await(underTest.deleteFieldValues(clientId, apiContext, apiVersion))
      result shouldBe true
    }

  }
}
