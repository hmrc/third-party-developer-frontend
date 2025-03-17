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

import java.util.UUID

import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.http.Status._
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.functional.syntax._
import play.api.libs.json.{JsPath, Json, Writes}
import play.api.{Application, Configuration, Mode}
import uk.gov.hmrc.http.{HeaderCarrier, _}

import uk.gov.hmrc.apiplatform.modules.applications.subscriptions.domain.models.{FieldName, FieldValue}
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.SubscriptionsBuilder
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.SubscriptionFieldsConnectorDomain._
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.SubscriptionFieldsConnectorJsonFormatters._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.ApiSubscriptionFields._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.{AccessRequirements, Fields}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WireMockExtensions

class SubscriptionFieldsConnectorSpec extends BaseConnectorIntegrationSpec with GuiceOneAppPerSuite with WireMockExtensions with SubscriptionsBuilder {
  private val apiKey: String = UUID.randomUUID().toString
  private val clientId       = ClientId(UUID.randomUUID().toString)
  private val apiContext     = ApiContext("i-am-a-test")
  private val apiVersion     = ApiVersionNbr("1.0")
  private val urlPrefix      = "/field"

  implicit val writesFieldDefinition: Writes[FieldDefinition] = (
    (JsPath \ "name").write[FieldName] and
      (JsPath \ "description").write[String] and
      (JsPath \ "shortDescription").write[String] and
      (JsPath \ "hint").write[String] and
      (JsPath \ "type").write[String] and
      (JsPath \ "access").write[AccessRequirements]
  )(unlift(FieldDefinition.unapply))

  implicit val writesApiFieldDefinitions: Writes[ApiFieldDefinitions]               = Json.writes[ApiFieldDefinitions]
  implicit val writesApplicationApiFieldValues: Writes[ApplicationApiFieldValues]   = Json.writes[ApplicationApiFieldValues]
  implicit val writesAllApiFieldDefinitionsResponse: Writes[AllApiFieldDefinitions] = Json.writes[AllApiFieldDefinitions]

  private val stubConfig = Configuration(
    "microservice.services.api-subscription-fields-production.port" -> stubPort,
    "microservice.services.api-subscription-fields-sandbox.port"    -> stubPort,
    "api-subscription-fields-production.api-key"                    -> "",
    "api-subscription-fields-production.use-proxy"                  -> false,
    "api-subscription-fields-sandbox.api-key"                       -> apiKey,
    "api-subscription-fields-sandbox.use-proxy"                     -> true
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
    implicit val hc: HeaderCarrier = HeaderCarrier()
    val underTest                  = app.injector.instanceOf[ProductionSubscriptionFieldsConnector]
  }

  trait SandboxSetup {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    val underTest                  = app.injector.instanceOf[SandboxSubscriptionFieldsConnector]
  }

  "saveFieldValues" should {
    val fieldsValues        = rawFields("field001" -> "value001", "field002" -> "value002")
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
