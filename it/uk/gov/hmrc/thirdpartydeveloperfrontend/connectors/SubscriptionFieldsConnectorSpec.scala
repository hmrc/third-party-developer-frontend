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

package uk.gov.hmrc.thirdpartydeveloperfrontend.connectors

import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.SubscriptionsBuilder
import com.github.tomakehurst.wiremock.client.WireMock._
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.SubscriptionFieldsConnectorDomain._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.ApiContext
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.ApiIdentifier
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.ApiVersion
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.ClientId
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.AccessRequirements
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.ApiSubscriptionFields._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.FieldName
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.FieldValue
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.Fields
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.Configuration
import play.api.Mode
import play.api.http.Status._
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.functional.syntax._
import play.api.libs.json.JsPath
import play.api.libs.json.Json
import play.api.libs.json.Writes
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WireMockExtensions

import java.util.UUID

import SubscriptionFieldsConnectorJsonFormatters._


class SubscriptionFieldsConnectorSpec extends BaseConnectorIntegrationSpec with GuiceOneAppPerSuite with WireMockExtensions with SubscriptionsBuilder {
  private val apiKey: String = UUID.randomUUID().toString
  private val clientId = ClientId(UUID.randomUUID().toString)
  private val apiContext = ApiContext("i-am-a-test")
  private val apiVersion = ApiVersion("1.0")
  private val apiIdentifier = ApiIdentifier(apiContext, apiVersion)
  private val fieldsId = UUID.randomUUID()
  private val urlPrefix = "/field"
  
  implicit val writesFieldDefinition: Writes[FieldDefinition] = (
    (JsPath \ "name").write[FieldName] and
    (JsPath \ "description").write[String] and
    (JsPath \ "shortDescription").write[String] and
    (JsPath \ "hint").write[String] and
    (JsPath \ "type").write[String] and
    (JsPath \ "access").write[AccessRequirements]
  )(unlift(FieldDefinition.unapply))
  
  implicit val writesApiFieldDefinitions: Writes[ApiFieldDefinitions] = Json.writes[ApiFieldDefinitions]
  implicit val writesApplicationApiFieldValues: Writes[ApplicationApiFieldValues] = Json.writes[ApplicationApiFieldValues]
  implicit val writesAllApiFieldDefinitionsResponse: Writes[AllApiFieldDefinitions] = Json.writes[AllApiFieldDefinitions]

  private val stubConfig = Configuration(
    "microservice.services.api-subscription-fields-production.port" -> stubPort,
    "microservice.services.api-subscription-fields-sandbox.port" -> stubPort,
    "api-subscription-fields-production.api-key" -> "",
    "api-subscription-fields-production.use-proxy" -> false,
    "api-subscription-fields-sandbox.api-key" -> apiKey,
    "api-subscription-fields-sandbox.use-proxy" -> true
  )

  def fields(tpl: (FieldName, FieldValue)*): Fields.Alias = Map(tpl: _*)

  def rawFields(tpl: (String, String)*): Fields.Alias = tpl.toSeq.map(t => (FieldName(t._1), FieldValue(t._2))).toMap


  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure(stubConfig)
      .overrides(bind[ConnectorMetrics].to[NoopConnectorMetrics])
      .in(Mode.Test)
      .build()

  trait Setup {
    implicit val hc = HeaderCarrier()
    val underTest = app.injector.instanceOf[ProductionSubscriptionFieldsConnector]
  }

  trait ProxiedSetup {
    implicit val hc = HeaderCarrier()
    val underTest = app.injector.instanceOf[SandboxSubscriptionFieldsConnector]
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

    val getUrl = s"${urlPrefix}/application/${clientId.value}/context/${apiContext.value}/version/${apiVersion.value}"

    "return subscription fields for an API" in new Setup {
      stubFor(
        get(urlEqualTo(getUrl))
        .willReturn(
            aResponse()
            .withStatus(OK)
            .withJsonBody(subscriptionFields)
            .withHeader("content-type", "application/json")))

      val result = await(underTest.fetchFieldsValuesWithPrefetchedDefinitions(clientId, apiIdentifier, prefetchedDefinitions))

      result shouldBe expectedResults
    }

    "fail when api-subscription-fields returns a 500" in new Setup {
      stubFor(
        get(urlEqualTo(getUrl))
        .willReturn(
            aResponse()
            .withStatus(INTERNAL_SERVER_ERROR)
        )
      )

      intercept[UpstreamErrorResponse] {
        await(underTest.fetchFieldsValuesWithPrefetchedDefinitions(clientId, apiIdentifier, prefetchedDefinitions))
      }.statusCode shouldBe 500
    }

    "return empty when api-subscription-fields returns a 404" in new Setup {
      stubFor(
        get(urlEqualTo(getUrl))
        .willReturn(
            aResponse()
            .withStatus(NOT_FOUND)
        )
      )

      val result = await(underTest.fetchFieldsValuesWithPrefetchedDefinitions(clientId, apiIdentifier, prefetchedDefinitions))

      result shouldBe Seq(subscriptionFieldValue.copy(value = FieldValue.empty))
    }

    "send the x-api-header key when retrieving subscription fields for an API" in new ProxiedSetup {
      stubFor(
        get(urlEqualTo(getUrl))
        .withHeader(ProxiedHttpClient.API_KEY_HEADER_NAME, equalTo(apiKey))
        .willReturn(
            aResponse()
            .withStatus(NOT_FOUND)
        )
      )

      await(underTest.fetchFieldsValuesWithPrefetchedDefinitions(clientId, apiIdentifier, prefetchedDefinitions))
    }
  }

  "fetchAllFieldDefinitions" should {

    val url = "/definition"

    "return all field definitions" in new Setup {

      val definitions = List(
        FieldDefinition(FieldName("field1"), "desc1", "sdesc1", "hint1", "some type", AccessRequirements.Default),
        FieldDefinition(FieldName("field2"), "desc2", "sdesc2", "hint2", "some other type", AccessRequirements.Default)
      )

      val validResponse =
        AllApiFieldDefinitions(apis = Seq(ApiFieldDefinitions(apiContext, apiVersion, definitions)))

      stubFor(
        get(urlEqualTo(url))
        .willReturn(
            aResponse()
            .withStatus(OK)
            .withJsonBody(validResponse)
        )
      )
      
      val result = await(underTest.fetchAllFieldDefinitions())

      val expectedResult = Map(apiIdentifier -> definitions.map(toDomain))

      result shouldBe expectedResult
    }

    "fail when api-subscription-fields returns a 500" in new Setup {

      stubFor(
        get(urlEqualTo(url))
        .willReturn(
            aResponse()
            .withStatus(INTERNAL_SERVER_ERROR)
        )
      )

      intercept[UpstreamErrorResponse] {
        await(underTest.fetchAllFieldDefinitions())
      }.statusCode shouldBe 500
    }

    "fail when api-subscription-fields returns unexpected response" in new Setup {

      stubFor(
        get(urlEqualTo(url))
        .willReturn(
            aResponse()
            .withStatus(NOT_FOUND)
        )
      )

      private val result =
        await(underTest.fetchAllFieldDefinitions())

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
      stubFor(
        get(urlEqualTo(url))
        .willReturn(
            aResponse()
            .withStatus(OK)
            .withJsonBody(validResponse)
        )
      )
      
      val result = await(underTest.fetchFieldDefinitions(apiContext, apiVersion))

      result shouldBe expectedDefinitions
    }

    "fail when api-subscription-fields returns a 500" in new Setup {
      stubFor(
        get(urlEqualTo(url))
        .willReturn(
            aResponse()
            .withStatus(INTERNAL_SERVER_ERROR)
        )
      )
      
      intercept[UpstreamErrorResponse] {
        await(underTest.fetchFieldDefinitions(apiContext, apiVersion))
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

      stubFor(
        get(urlEqualTo(definitionsUrl))
        .willReturn(
            aResponse()
            .withStatus(OK)
            .withJsonBody(validDefinitionsResponse)
        )
      )

      stubFor(
        get(urlEqualTo(valuesUrl))
        .willReturn(
            aResponse()
            .withStatus(OK)
            .withJsonBody(validValuesResponse)
        )
      )

      private val result = await(underTest.fetchFieldValues(clientId, apiContext, apiVersion))

      result shouldBe expectedFieldValues
    }

    "fail when fetching field definitions returns a 500" in new Setup {
      stubFor(
        get(urlEqualTo(definitionsUrl))
        .willReturn(
            aResponse()
            .withStatus(INTERNAL_SERVER_ERROR)
        )
      )

      intercept[UpstreamErrorResponse] {
        await(underTest.fetchFieldValues(clientId, apiContext, apiVersion))
      }
    }

    "fail when fetching field definition values returns a 500" in new Setup {
      stubFor(
        get(urlEqualTo(definitionsUrl))
        .willReturn(
            aResponse()
            .withStatus(OK)
            .withJsonBody(validDefinitionsResponse)
        )
      )

      stubFor(
        get(urlEqualTo(valuesUrl))
        .willReturn(
            aResponse()
            .withStatus(INTERNAL_SERVER_ERROR)
        )
      )

      intercept[UpstreamErrorResponse] {
        await(underTest.fetchFieldValues(clientId, apiContext, apiVersion))
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
      stubFor(
        put(urlEqualTo(putUrl))
        .withJsonRequestBody(subFieldsPutRequest)
        .willReturn(
            aResponse()
            .withStatus(OK)
        )
      )

      val result = await(underTest.saveFieldValues(clientId, apiContext, apiVersion, fieldsValues))

      result shouldBe SaveSubscriptionFieldsSuccessResponse
    }

    "fail when api-subscription-fields returns a 500" in new Setup {
      stubFor(
        put(urlEqualTo(putUrl))
        .withJsonRequestBody(subFieldsPutRequest)
        .willReturn(
            aResponse()
            .withStatus(INTERNAL_SERVER_ERROR)
        )
      )
         
      intercept[UpstreamErrorResponse] {
        await(underTest.saveFieldValues(clientId, apiContext, apiVersion, fieldsValues))
      }
    }

    "fail when api-subscription-fields returns a 404" in new Setup {
      stubFor(
        put(urlEqualTo(putUrl))
        .withJsonRequestBody(subFieldsPutRequest)
        .willReturn(
            aResponse()
            .withStatus(NOT_FOUND)
        )
      )
      intercept[UpstreamErrorResponse] {
        await(underTest.saveFieldValues(clientId, apiContext, apiVersion, fieldsValues))
      }.statusCode shouldBe NOT_FOUND
    }

    "fail when api-subscription-fields returns a 400 with validation error messages" in new Setup {
      stubFor(
        put(urlEqualTo(putUrl))
        .withJsonRequestBody(subFieldsPutRequest)
        .willReturn(
            aResponse()
            .withStatus(BAD_REQUEST)
            .withBody("""{"field1": "error1"}""")
        )
      )
      
      val result = await(underTest.saveFieldValues(clientId, apiContext, apiVersion, fieldsValues))

      result shouldBe SaveSubscriptionFieldsFailureResponse(Map("field1" -> "error1"))
    }
  }

  "deleteFieldValues" should {

    val url = s"$urlPrefix/application/${clientId.value}/context/${apiContext.value}/version/${apiVersion.value}"

    "return success after delete call has returned 204 NO CONTENT" in new Setup {
      stubFor(
        delete(urlEqualTo(url))
        .willReturn(
            aResponse()
            .withStatus(NO_CONTENT)
        )
      )

      val result = await(underTest.deleteFieldValues(clientId, apiContext, apiVersion))

      result shouldBe FieldsDeleteSuccessResult
    }

    "return failure if api-subscription-fields returns unexpected status" in new Setup {
      stubFor(
        delete(urlEqualTo(url))
        .willReturn(
            aResponse()
            .withStatus(ACCEPTED)
        )
      )
      
      val result = await(underTest.deleteFieldValues(clientId, apiContext, apiVersion))

      result shouldBe FieldsDeleteFailureResult
    }

    "return failure when api-subscription-fields returns a 500" in new Setup {
      stubFor(
        delete(urlEqualTo(url))
        .willReturn(
            aResponse()
            .withStatus(INTERNAL_SERVER_ERROR)
        )
      )
      val result = await(underTest.deleteFieldValues(clientId, apiContext, apiVersion))

      result shouldBe FieldsDeleteFailureResult
    }

    "return success when api-subscription-fields returns a 404" in new Setup {
      stubFor(
        delete(urlEqualTo(url))
        .willReturn(
            aResponse()
            .withStatus(NOT_FOUND)
        )
      )   

      val result = await(underTest.deleteFieldValues(clientId, apiContext, apiVersion))

      result shouldBe FieldsDeleteSuccessResult
    }
  }
}
