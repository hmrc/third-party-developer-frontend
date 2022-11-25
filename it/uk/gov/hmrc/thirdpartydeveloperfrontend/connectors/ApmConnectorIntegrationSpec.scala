package uk.gov.hmrc.thirdpartydeveloperfrontend.connectors

import com.github.tomakehurst.wiremock.client.WireMock._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors._
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status._
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Configuration, Mode, Application => PlayApplication}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.UserId
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.emailpreferences.APICategoryDisplayDetails
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WireMockExtensions
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.{Application, ApplicationId, ApplicationWithSubscriptionData, ClientId, Environment}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.ApiIdentifier
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.ApiContext
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.ApiVersion
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.ApplicationUpdateSuccessful
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.ApplicationNotFound
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.ApiType.REST_API
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.services.{ApiDefinitionsJsonFormatters, CombinedApiJsonFormatters}
import play.api.libs.json.Json
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.stubs.ApiPlatformMicroserviceStub
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.{ApiData, ApiSubscriptionFields, FieldName}

import java.time.{LocalDateTime, Period}

class ApmConnectorIntegrationSpec extends BaseConnectorIntegrationSpec with GuiceOneAppPerSuite with WireMockExtensions  with ApmConnectorJsonFormatters {

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
    val underTest = app.injector.instanceOf[ApmConnector]

  }

  "fetchApplicationById" should {
    val applicationId = ApplicationId.random
    val application = Application(applicationId, ClientId("someId"), "someName", LocalDateTime.now(), None, None, Period.ofDays(547), Environment.PRODUCTION, None, Set.empty)
    val applicationWithSubscriptionData: ApplicationWithSubscriptionData = ApplicationWithSubscriptionData(application = application, subscriptions = Set.empty, subscriptionFieldValues = Map.empty)

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

  "fetchAllCombinedAPICategories" should {
    val category1 = APICategoryDisplayDetails("CATEGORY_1", "Category 1")
    val category2 = APICategoryDisplayDetails("CATEGORY_2", "Category 2")

    "return all API Category details" in new Setup {
      ApiPlatformMicroserviceStub.stubFetchAllCombinedAPICategories(List(category1, category2))

      val result: Either[Throwable, List[APICategoryDisplayDetails]] = await(underTest.fetchAllCombinedAPICategories())
      result match {
        case Right(x) => x.size should be(2)
          x should contain only (category1, category2)
        case _        => fail()
      }

    }
  }

  "fetchCombinedApi" should {
    "retrieve an CombinedApi based on a serviceName" in new Setup {
      val serviceName = "api1"
      val displayName = "API 1"
      val expectedApi = CombinedApi(serviceName, displayName, List(CombinedApiCategory("VAT")), REST_API)
      ApiPlatformMicroserviceStub
        .stubCombinedApiByServiceName(serviceName, Json.toJson(expectedApi).toString())
      val result: Either[Throwable, CombinedApi] = await(underTest.fetchCombinedApi("api1"))
      result match {
        case Right(x) => x.serviceName shouldBe serviceName
          x.displayName shouldBe displayName
        case _        => fail()
      }

    }

    "fail on Upstream5xxResponse when the call return a 500" in new Setup {
      ApiPlatformMicroserviceStub.stubCombinedApiByServiceNameFailure(INTERNAL_SERVER_ERROR)

      val result = await(underTest.fetchCombinedApi("api1"))
      result match {
        case Left(_: UpstreamErrorResponse) => succeed
        case _                              => fail()
      }
    }

    "throw notfound when the api is not found" in new Setup {
      ApiPlatformMicroserviceStub.stubCombinedApiByServiceNameFailure(NOT_FOUND)

      val result = await(underTest.fetchCombinedApi("api1"))
      result match {
        case Left(e: UpstreamErrorResponse) => e.statusCode shouldBe NOT_FOUND
        case _                              => fail()
      }

    }
  }

  "fetchAllPossibleSubscriptions" should {
    val applicationId = ApplicationId.random

    "return api data when successful" in new Setup {
      val response = Map(ApiContext.random ->  ApiData("serviceName", "name", isTestSupport = false, Map.empty, List.empty))
      ApiPlatformMicroserviceStub.stubFetchAllPossibleSubscriptions(applicationId, Json.toJson(response).toString())
      val result: Map[ApiContext, ApiData] =  await(underTest.fetchAllPossibleSubscriptions(applicationId))
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
      val userId = UserId.random
      val serviceName = "api1"
      val name = "API 1"
      ApiPlatformMicroserviceStub
        .stubFetchApiDefinitionsVisibleToUser(userId,
          s"""[{ "serviceName": "$serviceName", "name": "$name", "description": "", "context": "context", "categories": ["AGENT", "VAT"] }]""")
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

  "subscribe to api" should {
    val applicationId = ApplicationId.random
    val apiIdentifier = ApiIdentifier(ApiContext("app1"), ApiVersion("2.0"))

    "subscribe application to an api" in new Setup {

      ApiPlatformMicroserviceStub
        .stubSubscribeToApi(applicationId, apiIdentifier)
      val result = await(underTest.subscribeToApi(applicationId, apiIdentifier))

      result shouldBe ApplicationUpdateSuccessful
    }

    "throw ApplicationNotFound if the application cannot be found" in new Setup {
      ApiPlatformMicroserviceStub
        .stubSubscribeToApiFailure(applicationId, apiIdentifier)
      intercept[ApplicationNotFound](
        await(underTest.subscribeToApi(applicationId, apiIdentifier))
      )
    }
  }

}
