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

import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors._
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status._
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Application => PlayApplication, Configuration, Mode}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.{ApiAccess, ApiData, ApiDefinition, ApiStatus, ApiVersion, ServiceName}
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WireMockExtensions
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.{Application, ApplicationWithSubscriptionData}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.ApiType.REST_API
import play.api.libs.json.Json
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.stubs.ApiPlatformMicroserviceStub

import java.time.{LocalDateTime, Period}
import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ApiCategory

class ApmConnectorIntegrationSpec extends BaseConnectorIntegrationSpec with GuiceOneAppPerSuite with WireMockExtensions with ApmConnectorJsonFormatters {

  private val stubConfig = Configuration(
    "microservice.services.api-platform-microservice.port" -> stubPort
  )

  override def fakeApplication(): PlayApplication =
    GuiceApplicationBuilder()
      .configure(stubConfig)
      .overrides(bind[ConnectorMetrics].to[NoopConnectorMetrics])
      .in(Mode.Test)
      .build()

  trait Setup {
    implicit val hc = HeaderCarrier()
    val underTest   = app.injector.instanceOf[ApmConnector]

  }

  "fetchApplicationById" should {
    val applicationId                                                    = ApplicationId.random
    val application                                                      = Application(applicationId, ClientId("someId"), "someName", LocalDateTime.now(), None, None, Period.ofDays(547), Environment.PRODUCTION, None, Set.empty)
    val applicationWithSubscriptionData: ApplicationWithSubscriptionData =
      ApplicationWithSubscriptionData(application = application, subscriptions = Set.empty, subscriptionFieldValues = Map.empty)

    "return ApplicationData when successful" in new Setup {

      ApiPlatformMicroserviceStub.stubFetchApplicationById(applicationId, applicationWithSubscriptionData: ApplicationWithSubscriptionData)
      val result: Option[ApplicationWithSubscriptionData] = await(underTest.fetchApplicationById(applicationId))
      result shouldBe Some(applicationWithSubscriptionData)
    }

    "return None when not found returned" in new Setup {

      ApiPlatformMicroserviceStub.stubFetchApplicationByIdFailure(applicationId)
      val result: Option[ApplicationWithSubscriptionData] = await(underTest.fetchApplicationById(applicationId))
      result shouldBe None
    }
  }

  "fetchCombinedApi" should {
    "retrieve an CombinedApi based on a serviceName" in new Setup {
      val serviceName                            = ServiceName("api1")
      val displayName                            = "API 1"
      val expectedApi                            = CombinedApi(serviceName, displayName, List(ApiCategory.VAT), REST_API)
      ApiPlatformMicroserviceStub
        .stubCombinedApiByServiceName(serviceName.value, Json.toJson(expectedApi).toString())
      val result: Either[Throwable, CombinedApi] = await(underTest.fetchCombinedApi(ServiceName("api1")))
      result match {
        case Right(x) =>
          x.serviceName shouldBe serviceName
          x.displayName shouldBe displayName
        case _        => fail()
      }

    }

    "fail on Upstream5xxResponse when the call return a 500" in new Setup {
      ApiPlatformMicroserviceStub.stubCombinedApiByServiceNameFailure(INTERNAL_SERVER_ERROR)

      val result = await(underTest.fetchCombinedApi(ServiceName("api1")))
      result match {
        case Left(_: UpstreamErrorResponse) => succeed
        case _                              => fail()
      }
    }

    "throw notfound when the api is not found" in new Setup {
      ApiPlatformMicroserviceStub.stubCombinedApiByServiceNameFailure(NOT_FOUND)

      val result = await(underTest.fetchCombinedApi(ServiceName("api1")))
      result match {
        case Left(e: UpstreamErrorResponse) => e.statusCode shouldBe NOT_FOUND
        case _                              => fail()
      }

    }
  }

  "fetchAllPossibleSubscriptions" should {
    val applicationId = ApplicationId.random

    "return api data when successful" in new Setup {
      val apiData                          = ApiData(
        serviceName = ServiceName("serviceName"),
        serviceBaseUrl = "http://serviceBaseUrl",
        name = "name",
        description = "Description",
        context = ApiContext("test-api-context-1"),
        versions = Map(ApiVersionNbr("1.0") ->
          ApiVersion(ApiVersionNbr("1.0"), ApiStatus.STABLE, ApiAccess.PUBLIC, List.empty)),
        isTestSupport = false,
        categories = List(ApiCategory.EXAMPLE)
      )
      val response                         = Map(ApiContext.random -> apiData)
      ApiPlatformMicroserviceStub.stubFetchAllPossibleSubscriptions(applicationId, Json.toJson(response).toString())
      val result: Map[ApiContext, ApiData] = await(underTest.fetchAllPossibleSubscriptions(applicationId))
      result shouldBe response
    }

    "fail on Upstream5xxResponse" in new Setup {
      ApiPlatformMicroserviceStub.stubFetchAllPossibleSubscriptionsFailure(applicationId)

      intercept[UpstreamErrorResponse] {
        await(underTest.fetchAllPossibleSubscriptions(applicationId))
      }
    }
  }

  "fetchApiDefinitionsVisibleToUser" should {

    "retrieve a list of service a user can see" in new Setup {
      val userId                     = UserId.random
      val serviceName                = ServiceName("api1")
      val name                       = "API 1"
      ApiPlatformMicroserviceStub
        .stubFetchApiDefinitionsVisibleToUser(
          userId,
          s"""[{ "serviceName": "$serviceName", "serviceBaseUrl": "http://serviceBaseUrl", "name": "$name", "description": "", "context": "context", "versions": [], "categories": ["AGENTS", "VAT"] }]"""
        )
      val result: Seq[ApiDefinition] = await(underTest.fetchApiDefinitionsVisibleToUser(userId))
      result.head.serviceName shouldBe serviceName
      result.head.name shouldBe name
    }

    "fail on Upstream5xxResponse when the call return a 500" in new Setup {
      val userId = UserId.random
      ApiPlatformMicroserviceStub.stubFetchApiDefinitionsVisibleToUserFailure(userId)

      intercept[UpstreamErrorResponse] {
        await(underTest.fetchApiDefinitionsVisibleToUser(userId))
      }
    }
  }
}
