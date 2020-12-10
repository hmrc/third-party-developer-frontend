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

import akka.pattern.FutureTimeoutSupport
import builder.SubscriptionsBuilder
import config.ApplicationConfig
import connectors.SubscriptionFieldsConnectorDomain._
import domain.models.apidefinitions.{ApiContext, ApiIdentifier, ApiVersion}
import domain.models.applications.{ClientId, Environment}
import domain.models.subscriptions.{AccessRequirements, FieldName, FieldValue, Fields}
import domain.models.subscriptions.ApiSubscriptionFields._
import helpers.FutureTimeoutSupportImpl
import play.api.http.Status._
import play.api.libs.json.Json
import uk.gov.hmrc.http.{HttpResponse, _}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import utils.AsyncHmrcSpec

import scala.concurrent.ExecutionContext
import scala.concurrent.Future.{failed, successful}
import akka.actor.ActorSystem
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

class SubscriptionFieldsConnectorSpec extends AsyncHmrcSpec with GuiceOneAppPerSuite with SubscriptionsBuilder {
  def fields(tpl: (FieldName, FieldValue)*): Fields.Alias =
    Map(tpl: _*)

  // TODO Remove asap
  def rawFields(tpl: (String, String)*): Fields.Alias = tpl.toSeq.map(t => (FieldName(t._1), FieldValue(t._2))).toMap

  private implicit val hc: HeaderCarrier = HeaderCarrier()

  private val clientId = ClientId(UUID.randomUUID().toString)
  private val apiContext = ApiContext("i-am-a-test")
  private val apiVersion = ApiVersion("1.0")
  private val apiIdentifier = ApiIdentifier(apiContext, apiVersion)
  private val fieldsId = UUID.randomUUID()
  private val urlPrefix = "/field"
  private val upstream500Response = UpstreamErrorResponse("", INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR)
  private val futureTimeoutSupport = new FutureTimeoutSupportImpl

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
      app.actorSystem,
      futureTimeoutSupport
    )
  }

  trait ProxiedSetup extends Setup {
    import scala.concurrent.ExecutionContext.Implicits.global

    when(mockProxiedHttpClient.withHeaders(*))
      .thenReturn(mockProxiedHttpClient)

    override val subscriptionFieldsConnector =
      new SubscriptionFieldsTestConnector(
        useProxy = true,
        bearerToken,
        apiKey,
        mockHttpClient,
        mockProxiedHttpClient,
        mockAppConfig,
        app.actorSystem,
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
      s"$urlPrefix/application/${clientId.value}/context/${apiContext.value}/version/${apiVersion.value}"

    "return subscription fields for an API" in new Setup {
      when(
        mockHttpClient
          .GET[Option[ApplicationApiFieldValues]](eqTo(getUrl))(*, *, *)
      ).thenReturn(successful(Some(subscriptionFields)))

      private val result = await(
        subscriptionFieldsConnector
          .fetchFieldsValuesWithPrefetchedDefinitions(clientId, apiIdentifier, prefetchedDefinitions)
      )

      result shouldBe expectedResults
    }

    "fail when api-subscription-fields returns a 500" in new Setup {

      when(
        mockHttpClient
          .GET[Option[ApplicationApiFieldValues]](eqTo(getUrl))(*, *, *)
      ).thenReturn(failed(upstream500Response))

      intercept[UpstreamErrorResponse] {
        await(
          subscriptionFieldsConnector
            .fetchFieldsValuesWithPrefetchedDefinitions(clientId, apiIdentifier, prefetchedDefinitions)
        )
      }.statusCode shouldBe 500
    }

    "return empty when api-subscription-fields returns a 404" in new Setup {

      when(mockHttpClient.GET[Option[ApplicationApiFieldValues]](eqTo(getUrl))(*, *, *))
        .thenReturn(successful(None))

      private val result = await(
        subscriptionFieldsConnector
          .fetchFieldsValuesWithPrefetchedDefinitions(clientId, apiIdentifier, prefetchedDefinitions)
      )
      result shouldBe Seq(subscriptionFieldValue.copy(value = FieldValue.empty))
    }

    "send the x-api-header key when retrieving subscription fields for an API" in new ProxiedSetup {

      when(
        mockProxiedHttpClient
          .GET[Option[ApplicationApiFieldValues]](*)(*, *, *)
      ).thenReturn(successful(Some(subscriptionFields)))

      await(
        subscriptionFieldsConnector
          .fetchFieldsValuesWithPrefetchedDefinitions(clientId, apiIdentifier, prefetchedDefinitions)
      )

      verify(mockProxiedHttpClient).withHeaders(eqTo(apiKey))
    }
  }

  "fetchAllFieldDefinitions" should {

    val url = "/definition"

    "return all field definitions" in new Setup {

      val definitions = List(
        FieldDefinition(FieldName("field1"), "desc1", "sdesc1", "hint1", "some type", AccessRequirements.Default),
        FieldDefinition(FieldName("field2"), "desc2", "sdesc2", "hint2", "some other type", AccessRequirements.Default)
      )

      private val validResponse =
        AllApiFieldDefinitions(apis = Seq(ApiFieldDefinitions(apiContext, apiVersion, definitions)))

      when(
        mockHttpClient
          .GET[Option[AllApiFieldDefinitions]](eqTo(url))(*, *, *)
      ).thenReturn(successful(Some(validResponse)))

      private val result =
        await(subscriptionFieldsConnector.fetchAllFieldDefinitions())

      val expectedResult = Map(apiIdentifier -> definitions.map(toDomain))

      result shouldBe expectedResult
    }

    "fail when api-subscription-fields returns a 500" in new Setup {

      when(
        mockHttpClient
          .GET[Option[AllApiFieldDefinitions]](eqTo(url))(*, *, *)
      ).thenReturn(failed(upstream500Response))

      intercept[UpstreamErrorResponse] {
        await(subscriptionFieldsConnector.fetchAllFieldDefinitions())
      }.statusCode shouldBe 500
    }

    "fail when api-subscription-fields returns unexpected response" in new Setup {

      when(
        mockHttpClient
          .GET[Option[AllApiFieldDefinitions]](eqTo(url))(*, *, *)
      ).thenReturn(successful(None))

      private val result =
        await(subscriptionFieldsConnector.fetchAllFieldDefinitions())

      result shouldBe Map.empty[String, String]
    }
  }

  "fetchFieldDefinitions" should {
    val url = s"/definition/context/${apiContext.value}/version/${apiVersion.value}"

    val definitionsFromRestService = List(
      FieldDefinition(FieldName("field1"), "desc1", "sdesc2", "hint1", "some type", AccessRequirements.Default)
    )

    val expectedDefinitions =
      List(SubscriptionFieldDefinition(FieldName("field1"), "desc1", "sdesc2", "hint1", "some type", AccessRequirements.Default))

    val validResponse =
      ApiFieldDefinitions(apiContext, apiVersion, definitionsFromRestService)

    "return definitions" in new Setup {
      when(
        mockHttpClient.GET[Option[ApiFieldDefinitions]](eqTo(url))(*, *, *)
      ).thenReturn(successful(Some(validResponse)))

      private val result = await(
        subscriptionFieldsConnector
          .fetchFieldDefinitions(apiContext, apiVersion)
      )

      result shouldBe expectedDefinitions
    }

    "fail when api-subscription-fields returns a 500" in new Setup {

      when(
        mockHttpClient.GET[Option[ApiFieldDefinitions]](eqTo(url))(*, *, *)
      ).thenReturn(failed(upstream500Response))

      intercept[UpstreamErrorResponse] {
        await(subscriptionFieldsConnector.fetchFieldDefinitions(apiContext, apiVersion))
      }
    }
  }

  "fetchFieldValues" should {
    val definitionsUrl = s"/definition/context/${apiContext.value}/version/${apiVersion.value}"
    val valuesUrl =
      s"/field/application/${clientId.value}/context/${apiContext.value}/version/${apiVersion.value}"

    val definitionsFromRestService = List(
      FieldDefinition(FieldName("field1"), "desc1", "sdesc1", "hint1", "some type", AccessRequirements.Default)
    )

    val validDefinitionsResponse: ApiFieldDefinitions =
      ApiFieldDefinitions(apiContext, apiVersion, definitionsFromRestService)

    "return field values" in new Setup {
      val expectedDefinitions =
        definitionsFromRestService.map(d => SubscriptionFieldDefinition(d.name, d.description, d.shortDescription, d.hint, d.`type`, AccessRequirements.Default))
      val expectedFieldValues =
        expectedDefinitions.map(definition => SubscriptionFieldValue(definition, FieldValue("my-value")))

      val fieldsValues: Fields.Alias =
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
          .GET[Option[ApiFieldDefinitions]](eqTo(definitionsUrl))(*, *, *)
      ).thenReturn(successful(Some(validDefinitionsResponse)))

      when(
        mockHttpClient
          .GET[Option[ApplicationApiFieldValues]](eqTo(valuesUrl))(*, *, *)
      ).thenReturn(successful(Some(validValuesResponse)))

      private val result = await(
        subscriptionFieldsConnector
          .fetchFieldValues(clientId, apiContext, apiVersion)
      )

      result shouldBe expectedFieldValues
    }

    "fail when fetching field definitions returns a 500" in new Setup {
      when(
        mockHttpClient
          .GET[Option[ApiFieldDefinitions]](eqTo(definitionsUrl))(*, *, *)
      ).thenReturn(failed(upstream500Response))

      intercept[UpstreamErrorResponse] {
        await(
          subscriptionFieldsConnector
            .fetchFieldValues(clientId, apiContext, apiVersion)
        )
      }
    }

    "fail when fetching field definition values returns a 500" in new Setup {
      when(
        mockHttpClient
          .GET[Option[ApiFieldDefinitions]](eqTo(definitionsUrl))(*, *, *)
      ).thenReturn(successful(Some(validDefinitionsResponse)))

      when(
        mockHttpClient
          .GET[Option[ApiFieldDefinitions]](eqTo(valuesUrl))(*, *, *)
      ).thenReturn(failed(upstream500Response))

      intercept[UpstreamErrorResponse] {
        await(
          subscriptionFieldsConnector
            .fetchFieldValues(clientId, apiContext, apiVersion)
        )
      }
    }
  }

  "saveFieldValues" should {
    val fieldsValues = rawFields("field001" -> "value001", "field002" -> "value002")
    val subFieldsPutRequest = SubscriptionFieldsPutRequest(
      clientId,
      apiContext,
      apiVersion,
      fieldsValues
    )

    val putUrl = s"$urlPrefix/application/${clientId.value}/context/${apiContext.value}/version/${apiVersion.value}"

    "save the fields" in new Setup {
      val response = HttpResponse(OK,"")

      when(
        mockHttpClient.PUT[SubscriptionFieldsPutRequest, HttpResponse](
          eqTo(putUrl),
          eqTo(subFieldsPutRequest),
          any[Seq[(String, String)]]
        )(*, *, *, *)
      ).thenReturn(successful(response))

      val result = await(
        subscriptionFieldsConnector
          .saveFieldValues(clientId, apiContext, apiVersion, fieldsValues)
      )

      result shouldBe SaveSubscriptionFieldsSuccessResponse

      verify(mockHttpClient).PUT[SubscriptionFieldsPutRequest, HttpResponse](
        eqTo(putUrl),
        eqTo(subFieldsPutRequest),
        any[Seq[(String, String)]]
      )(*, *, *, *)
    }

    "fail when api-subscription-fields returns a 500" in new Setup {
      when(
        mockHttpClient.PUT[SubscriptionFieldsPutRequest, HttpResponse](
          eqTo(putUrl),
          eqTo(subFieldsPutRequest),
          any[Seq[(String, String)]]
        )(*, *, *, *)
      ).thenReturn(failed(upstream500Response))

      intercept[UpstreamErrorResponse] {
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
        )(*, *, *, *)
      ).thenReturn(successful(HttpResponse(NOT_FOUND,"")))

      intercept[UpstreamErrorResponse] {
        await(
          subscriptionFieldsConnector
            .saveFieldValues(clientId, apiContext, apiVersion, fieldsValues)
        )
      }.statusCode shouldBe NOT_FOUND
    }

    "fail when api-subscription-fields returns a 400 with validation error messages" in new Setup {
      val errorJson =
        """{
          |    "field1": "error1"
          |}""".stripMargin

      val response = HttpResponse(
        BAD_REQUEST,
        Json.parse(errorJson),
        Map.empty[String, Seq[String]]
      )

      when(
        mockHttpClient.PUT[SubscriptionFieldsPutRequest, HttpResponse](
          eqTo(putUrl),
          eqTo(subFieldsPutRequest),
          any[Seq[(String, String)]]
        )(*, *, *, *)
      ).thenReturn(successful(response))

      val result = await(
        subscriptionFieldsConnector
          .saveFieldValues(clientId, apiContext, apiVersion, fieldsValues)
      )

      result shouldBe SaveSubscriptionFieldsFailureResponse(Map("field1" -> "error1"))
    }
  }

  "deleteFieldValues" should {

    val url = s"$urlPrefix/application/${clientId.value}/context/${apiContext.value}/version/${apiVersion.value}"

    "return success after delete call has returned 204 NO CONTENT" in new Setup {
      when(mockHttpClient.DELETE[HttpResponse](eqTo(url), *)(*, *, *))
        .thenReturn(successful(HttpResponse(NO_CONTENT,"")))

      private val result = await(
        subscriptionFieldsConnector
          .deleteFieldValues(clientId, apiContext, apiVersion)
      )

      result shouldBe FieldsDeleteSuccessResult
    }

    "return failure if api-subscription-fields returns unexpected status" in new Setup {
      when(mockHttpClient.DELETE[HttpResponse](eqTo(url), *)(*, *, *))
        .thenReturn(successful(HttpResponse(ACCEPTED,"")))

      private val result = await(
        subscriptionFieldsConnector
          .deleteFieldValues(clientId, apiContext, apiVersion)
      )

      result shouldBe FieldsDeleteFailureResult
    }

    "return failure when api-subscription-fields returns a 500" in new Setup {

      when(mockHttpClient.DELETE[HttpResponse](eqTo(url), *)(*, *, *))
        .thenReturn(successful(HttpResponse(INTERNAL_SERVER_ERROR, "")))

      private val result = await(
        subscriptionFieldsConnector
          .deleteFieldValues(clientId, apiContext, apiVersion)
      )

      result shouldBe FieldsDeleteFailureResult
    }

    "return success when api-subscription-fields returns a 404" in new Setup {
      when(mockHttpClient.DELETE[HttpResponse](eqTo(url), *)(*, *, *))
        .thenReturn(successful(HttpResponse(NOT_FOUND,"")))

      private val result = await(
        subscriptionFieldsConnector
          .deleteFieldValues(clientId, apiContext, apiVersion)
      )

      result shouldBe FieldsDeleteSuccessResult
    }
  }
}
