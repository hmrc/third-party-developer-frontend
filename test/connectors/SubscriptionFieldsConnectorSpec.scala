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

import java.util.UUID

import akka.actor.ActorSystem
import akka.pattern.FutureTimeoutSupport
import config.ApplicationConfig
import connectors.SubscriptionFieldsConnector._
import domain.models.subscriptions.ApiSubscriptionFields._
import helpers.FutureTimeoutSupportImpl
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.{verify, when}
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.Status._
import play.api.libs.json.Json
import uk.gov.hmrc.http.{HttpResponse, _}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.{ExecutionContext, Future}
import builder.SubscriptionsBuilder
import domain.models.apidefinitions.APIIdentifier
import domain.models.applications.Environment
import domain.models.subscriptions.AccessRequirements

class SubscriptionFieldsConnectorSpec extends UnitSpec with ScalaFutures with MockitoSugar with SubscriptionsBuilder {
  def fields(tpl: (String, String)*): Map[String, String] =
    Map[String, String](tpl: _*)

  private implicit val hc: HeaderCarrier = HeaderCarrier()

  private val clientId = UUID.randomUUID().toString
  private val apiContext = "i-am-a-test"
  private val apiVersion = "1.0"
  private val apiIdentifier = APIIdentifier(apiContext, apiVersion)
  private val fieldsId = UUID.randomUUID()
  private val urlPrefix = "/field"
  private val upstream500Response = Upstream5xxResponse("", INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR)
  private val futureTimeoutSupport = new FutureTimeoutSupportImpl
  private val actorSystem = ActorSystem("test-actor-system")

  trait Setup {
    import scala.concurrent.ExecutionContext.Implicits.global

    val apiKey: String = UUID.randomUUID().toString
    val bearerToken: String = UUID.randomUUID().toString
    val mockHttpClient: HttpClient = mock[HttpClient]
    val mockProxiedHttpClient: ProxiedHttpClient = mock[ProxiedHttpClient]
    val mockAppConfig: ApplicationConfig = mock[ApplicationConfig]

    when(mockAppConfig.apiSubscriptionFieldsSandboxApiKey).thenReturn(apiKey)

    val subscriptionFieldsConnector = new SubscriptionFieldsTestConnector(
      useProxy = false,
      bearerToken = "",
      apiKey = "",
      mockHttpClient,
      mockProxiedHttpClient,
      mockAppConfig,
      actorSystem,
      futureTimeoutSupport
    )
  }

  trait ProxiedSetup extends Setup {
    import scala.concurrent.ExecutionContext.Implicits.global

    when(mockProxiedHttpClient.withHeaders(any(), any()))
      .thenReturn(mockProxiedHttpClient)

    override val subscriptionFieldsConnector =
      new SubscriptionFieldsTestConnector(
        useProxy = true,
        bearerToken,
        apiKey,
        mockHttpClient,
        mockProxiedHttpClient,
        mockAppConfig,
        actorSystem,
        futureTimeoutSupport
      )
  }

  class SubscriptionFieldsTestConnector(
      val useProxy: Boolean,
      val bearerToken: String,
      val apiKey: String,
      val httpClient: HttpClient,
      val proxiedHttpClient: ProxiedHttpClient,
      val appConfig: ApplicationConfig,
      val actorSystem: ActorSystem,
      val futureTimeout: FutureTimeoutSupport
  )(implicit val ec: ExecutionContext)
      extends AbstractSubscriptionFieldsConnector {
    val serviceBaseUrl = ""
    val environment: Environment = Environment.SANDBOX
  }

  private def squidProxyRelatedBadRequest = {
    new BadRequestException(
      "GET of 'https://api.development.tax.service.gov.uk:443/testing/api-subscription-fields/field/application/" +
        "xxxyyyzzz/context/api-platform-test/version/7.0' returned 400 (Bad Request). Response body " +
        "'<html>\n<head><title>400 Bad Request</title></head>\n<body bgcolor=\"white\">\n" +
        "<center><h1>400 Bad Request</h1></center>\n<hr><center>nginx</center>\n</body>\n</html>\n'"
    )
  }

  "fetchFieldsValuesWithPrefetchedDefinitions" should {

    val subscriptionFieldValue = buildSubscriptionFieldValue("my-name", Some("my-value"))
    val subscriptionDefinition = subscriptionFieldValue.definition

    val subscriptionFields =
      ApplicationApiFieldValues(
        clientId,
        apiContext,
        apiVersion,
        fieldsId,
        fields(subscriptionFieldValue.definition.name -> subscriptionFieldValue.value)
      )

    val expectedResults = Seq(subscriptionFieldValue)

    val prefetchedDefinitions =
      Map(apiIdentifier -> Seq(subscriptionDefinition))

    val getUrl =
      s"$urlPrefix/application/$clientId/context/$apiContext/version/$apiVersion"

    "return subscription fields for an API" in new Setup {
      when(
        mockHttpClient
          .GET[ApplicationApiFieldValues](eqTo(getUrl))(any(), any(), any())
      ).thenReturn(Future.successful(subscriptionFields))

      private val result = await(
        subscriptionFieldsConnector
          .fetchFieldsValuesWithPrefetchedDefinitions(clientId, apiIdentifier, prefetchedDefinitions)
      )

      result shouldBe expectedResults
    }

    "fail when api-subscription-fields returns a 500" in new Setup {

      when(
        mockHttpClient
          .GET[ApplicationApiFieldValues](eqTo(getUrl))(any(), any(), any())
      ).thenReturn(Future.failed(upstream500Response))

      intercept[Upstream5xxResponse] {
        await(
          subscriptionFieldsConnector
            .fetchFieldsValuesWithPrefetchedDefinitions(clientId, apiIdentifier, prefetchedDefinitions)
        )
      }
    }

    "return empty when api-subscription-fields returns a 404" in new Setup {

      when(mockHttpClient.GET[ApplicationApiFieldValues](eqTo(getUrl))(any(), any(), any()))
        .thenReturn(Future.failed(new NotFoundException("")))

      private val result = await(
        subscriptionFieldsConnector
          .fetchFieldsValuesWithPrefetchedDefinitions(clientId, apiIdentifier, prefetchedDefinitions)
      )
      result shouldBe Seq(subscriptionFieldValue.copy(value = ""))
    }

    "send the x-api-header key when retrieving subscription fields for an API" in new ProxiedSetup {

      when(
        mockProxiedHttpClient
          .GET[ApplicationApiFieldValues](any())(any(), any(), any())
      ).thenReturn(Future.successful(subscriptionFields))

      await(
        subscriptionFieldsConnector
          .fetchFieldsValuesWithPrefetchedDefinitions(clientId, apiIdentifier, prefetchedDefinitions)
      )

      verify(mockProxiedHttpClient).withHeaders(any(), eqTo(apiKey))
    }

    "when retry logic is enabled should retry on failure" in new Setup {

      when(mockAppConfig.retryCount).thenReturn(1)
      when(mockHttpClient.GET[ApplicationApiFieldValues](eqTo(getUrl))(any(), any(), any()))
        .thenReturn(
          Future.failed(squidProxyRelatedBadRequest),
          Future.successful(subscriptionFields)
        )

      private val result = await(
        subscriptionFieldsConnector.fetchFieldsValuesWithPrefetchedDefinitions(
          clientId,
          apiIdentifier,
          prefetchedDefinitions
        )
      )

      result shouldBe Seq(subscriptionFieldValue)
    }
  }

  "fetchAllFieldDefinitions" should {

    val url = "/definition"

    "return all field definitions" in new Setup {

      val definitions = List(
        FieldDefinition("field1", "desc1", "sdesc1", "hint1", "some type", AccessRequirements.Default),
        FieldDefinition("field2", "desc2", "sdesc2", "hint2", "some other type", AccessRequirements.Default)
      )

      private val validResponse =
        AllApiFieldDefinitions(apis = Seq(ApiFieldDefinitions(apiContext, apiVersion, definitions)))

      when(
        mockHttpClient
          .GET[AllApiFieldDefinitions](eqTo(url))(any(), any(), any())
      ).thenReturn(Future.successful(validResponse))

      private val result =
        await(subscriptionFieldsConnector.fetchAllFieldDefinitions())

      val expectedResult = Map(apiIdentifier -> definitions.map(toDomain))

      result shouldBe expectedResult
    }

    "fail when api-subscription-fields returns a 500" in new Setup {

      when(
        mockHttpClient
          .GET[AllApiFieldDefinitions](eqTo(url))(any(), any(), any())
      ).thenReturn(Future.failed(upstream500Response))

      intercept[Upstream5xxResponse] {
        await(subscriptionFieldsConnector.fetchAllFieldDefinitions())
      }
    }

    "fail when api-subscription-fields returns unexpected response" in new Setup {

      when(
        mockHttpClient
          .GET[AllApiFieldDefinitions](eqTo(url))(any(), any(), any())
      ).thenReturn(Future.failed(new NotFoundException("")))

      private val result =
        await(subscriptionFieldsConnector.fetchAllFieldDefinitions())

      result shouldBe Map.empty[String, String]
    }

    "when retry logic is enabled should retry on failure" in new Setup {

      val definitions = List(
        FieldDefinition("field1", "desc1", "sdesc1", "hint1", "some type", AccessRequirements.Default),
        FieldDefinition("field2", "desc2", "sdesc2", "hint2", "some other type", AccessRequirements.Default)
      )

      private val validResponse =
        AllApiFieldDefinitions(apis = Seq(ApiFieldDefinitions(apiContext, apiVersion, definitions)))

      when(mockAppConfig.retryCount).thenReturn(1)
      when(
        mockHttpClient
          .GET[AllApiFieldDefinitions](eqTo(url))(any(), any(), any())
      ).thenReturn(
        Future.failed(new BadRequestException("")),
        Future.successful(validResponse)
      )

      private val result =
        await(subscriptionFieldsConnector.fetchAllFieldDefinitions())

      val expectedResult = Map(apiIdentifier -> definitions.map(toDomain))

      result shouldBe expectedResult
    }
  }

  "fetchFieldDefinitions" should {
    val url = s"/definition/context/$apiContext/version/$apiVersion"

    val definitionsFromRestService = List(
      FieldDefinition("field1", "desc1", "sdesc2", "hint1", "some type", AccessRequirements.Default)
    )

    val expectedDefinitions =
      List(SubscriptionFieldDefinition("field1", "desc1", "sdesc2", "hint1", "some type", AccessRequirements.Default))

    val validResponse =
      ApiFieldDefinitions(apiContext, apiVersion, definitionsFromRestService)

    "return definitions" in new Setup {
      when(
        mockHttpClient.GET[ApiFieldDefinitions](eqTo(url))(any(), any(), any())
      ).thenReturn(Future.successful(validResponse))

      private val result = await(
        subscriptionFieldsConnector
          .fetchFieldDefinitions(apiContext, apiVersion)
      )

      result shouldBe expectedDefinitions
    }

    "fail when api-subscription-fields returns a 500" in new Setup {

      when(
        mockHttpClient.GET[ApiFieldDefinitions](eqTo(url))(any(), any(), any())
      ).thenReturn(Future.failed(upstream500Response))

      intercept[Upstream5xxResponse] {
        await(
          subscriptionFieldsConnector
            .fetchFieldDefinitions(apiContext, apiVersion)
        )
      }
    }

    "when retry logic is enabled should retry on failure" in new Setup {

      when(mockAppConfig.retryCount).thenReturn(1)
      when(
        mockHttpClient.GET[ApiFieldDefinitions](eqTo(url))(any(), any(), any())
      ).thenReturn(
        Future.failed(new BadRequestException("")),
        Future.successful(validResponse)
      )

      private val result = await(
        subscriptionFieldsConnector
          .fetchFieldDefinitions(apiContext, apiVersion)
      )

      result shouldBe expectedDefinitions
    }
  }

  "fetchFieldValues" should {
    val definitionsUrl = s"/definition/context/$apiContext/version/$apiVersion"
    val valuesUrl =
      s"/field/application/$clientId/context/$apiContext/version/$apiVersion"

    val definitionsFromRestService = List(
      FieldDefinition("field1", "desc1", "sdesc1", "hint1", "some type", AccessRequirements.Default)
    )

    val validDefinitionsResponse: ApiFieldDefinitions =
      ApiFieldDefinitions(apiContext, apiVersion, definitionsFromRestService)

    "return field values" in new Setup {
      val expectedDefinitions =
        definitionsFromRestService.map(d => SubscriptionFieldDefinition(d.name, d.description, d.shortDescription, d.hint, d.`type`, AccessRequirements.Default))
      val expectedFieldValues =
        expectedDefinitions.map(definition => SubscriptionFieldValue(definition, "my-value"))

      val fieldsValues: Map[String, String] =
        fields(expectedFieldValues.map(v => v.definition.name -> v.value): _*)

      val validValuesResponse: ApplicationApiFieldValues =
        ApplicationApiFieldValues(
          clientId,
          apiContext,
          apiVersion,
          fieldsId,
          fieldsValues
        )

      when(
        mockHttpClient
          .GET[ApiFieldDefinitions](eqTo(definitionsUrl))(any(), any(), any())
      ).thenReturn(Future.successful(validDefinitionsResponse))

      when(
        mockHttpClient
          .GET[ApplicationApiFieldValues](eqTo(valuesUrl))(any(), any(), any())
      ).thenReturn(Future.successful(validValuesResponse))

      private val result = await(
        subscriptionFieldsConnector
          .fetchFieldValues(clientId, apiContext, apiVersion)
      )

      result shouldBe expectedFieldValues
    }

    "fail when fetching field definitions returns a 500" in new Setup {
      when(
        mockHttpClient
          .GET[ApiFieldDefinitions](eqTo(definitionsUrl))(any(), any(), any())
      ).thenReturn(Future.failed(upstream500Response))

      intercept[Upstream5xxResponse] {
        await(
          subscriptionFieldsConnector
            .fetchFieldValues(clientId, apiContext, apiVersion)
        )
      }
    }

    "fail when fetching field definition values returns a 500" in new Setup {
      when(
        mockHttpClient
          .GET[ApiFieldDefinitions](eqTo(definitionsUrl))(any(), any(), any())
      ).thenReturn(Future.successful(validDefinitionsResponse))

      when(
        mockHttpClient
          .GET[ApiFieldDefinitions](eqTo(valuesUrl))(any(), any(), any())
      ).thenReturn(Future.failed(upstream500Response))

      intercept[Upstream5xxResponse] {
        await(
          subscriptionFieldsConnector
            .fetchFieldValues(clientId, apiContext, apiVersion)
        )
      }
    }
  }

  "saveFieldValues" should {
    val fieldsValues = fields("field001" -> "value001", "field002" -> "value002")
    val subFieldsPutRequest = SubscriptionFieldsPutRequest(
      clientId,
      apiContext,
      apiVersion,
      fieldsValues
    )

    val putUrl = s"$urlPrefix/application/$clientId/context/$apiContext/version/$apiVersion"

    "save the fields" in new Setup {
      val response = HttpResponse(OK)

      when(
        mockHttpClient.PUT[SubscriptionFieldsPutRequest, HttpResponse](
          eqTo(putUrl),
          eqTo(subFieldsPutRequest),
          any[Seq[(String, String)]]
        )(any(), any(), any(), any())
      ).thenReturn(Future.successful(response))

      val result = await(
        subscriptionFieldsConnector
          .saveFieldValues(clientId, apiContext, apiVersion, fieldsValues)
      )

      result shouldBe SaveSubscriptionFieldsSuccessResponse

      verify(mockHttpClient).PUT[SubscriptionFieldsPutRequest, HttpResponse](
        eqTo(putUrl),
        eqTo(subFieldsPutRequest),
        any[Seq[(String, String)]]
      )(any(), any(), any(), any())
    }

    "fail when api-subscription-fields returns a 500" in new Setup {
      when(
        mockHttpClient.PUT[SubscriptionFieldsPutRequest, HttpResponse](
          eqTo(putUrl),
          eqTo(subFieldsPutRequest),
          any[Seq[(String, String)]]
        )(any(), any(), any(), any())
      ).thenReturn(Future.failed(upstream500Response))

      intercept[Upstream5xxResponse] {
        await(
          subscriptionFieldsConnector
            .saveFieldValues(clientId, apiContext, apiVersion, fieldsValues)
        )
      }
    }

    "fail when api-subscription-fields returns a 404" in new Setup {
      when(
        mockHttpClient.PUT[SubscriptionFieldsPutRequest, HttpResponse](
          eqTo(putUrl),
          eqTo(subFieldsPutRequest),
          any[Seq[(String, String)]]
        )(any(), any(), any(), any())
      ).thenReturn(Future.failed(new NotFoundException("")))

      intercept[NotFoundException] {
        await(
          subscriptionFieldsConnector
            .saveFieldValues(clientId, apiContext, apiVersion, fieldsValues)
        )
      }
    }

    "fail when api-subscription-fields returns a 400 with validation error messages" in new Setup {
      val errorJson =
        """{
          |    "field1": "error1"
          |}""".stripMargin

      val response = HttpResponse(
        responseStatus = BAD_REQUEST,
        responseJson = Some(Json.parse(errorJson))
      )

      when(
        mockHttpClient.PUT[SubscriptionFieldsPutRequest, HttpResponse](
          eqTo(putUrl),
          eqTo(subFieldsPutRequest),
          any[Seq[(String, String)]]
        )(any(), any(), any(), any())
      ).thenReturn(Future.successful(response))

      val result = await(
        subscriptionFieldsConnector
          .saveFieldValues(clientId, apiContext, apiVersion, fieldsValues)
      )

      result shouldBe SaveSubscriptionFieldsFailureResponse(Map("field1" -> "error1"))
    }
  }

  "deleteFieldValues" should {
    import scala.concurrent.ExecutionContext.Implicits.global

    val url = s"$urlPrefix/application/$clientId/context/$apiContext/version/$apiVersion"

    "return success after delete call has returned 204 NO CONTENT" in new Setup {
      when(mockHttpClient.DELETE(url))
        .thenReturn(Future.successful(HttpResponse(NO_CONTENT)))

      private val result = await(
        subscriptionFieldsConnector
          .deleteFieldValues(clientId, apiContext, apiVersion)
      )

      result shouldBe FieldsDeleteSuccessResult
    }

    "return failure if api-subscription-fields returns unexpected status" in new Setup {
      when(mockHttpClient.DELETE(url))
        .thenReturn(Future.successful(HttpResponse(ACCEPTED)))

      private val result = await(
        subscriptionFieldsConnector
          .deleteFieldValues(clientId, apiContext, apiVersion)
      )

      result shouldBe FieldsDeleteFailureResult
    }

    "return failure when api-subscription-fields returns a 500" in new Setup {

      when(mockHttpClient.DELETE(url))
        .thenReturn(Future.failed(upstream500Response))

      private val result = await(
        subscriptionFieldsConnector
          .deleteFieldValues(clientId, apiContext, apiVersion)
      )

      result shouldBe FieldsDeleteFailureResult
    }

    "return success when api-subscription-fields returns a 404" in new Setup {
      when(mockHttpClient.DELETE(url))
        .thenReturn(Future.failed(new NotFoundException("")))

      private val result = await(
        subscriptionFieldsConnector
          .deleteFieldValues(clientId, apiContext, apiVersion)
      )

      result shouldBe FieldsDeleteSuccessResult
    }
  }
}
