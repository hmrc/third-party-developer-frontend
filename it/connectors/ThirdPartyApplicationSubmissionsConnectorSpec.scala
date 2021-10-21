package connectors

import java.util.UUID
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import utils._
import play.api.Configuration
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.inject.bind
import play.api.Mode
import play.api.{Application => PlayApplication}
import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.http.Status._
import modules.submissions.connectors.ThirdPartyApplicationSubmissionsConnector
import modules.submissions.connectors.ThirdPartyApplicationSubmissionsConnector._
import uk.gov.hmrc.http.HeaderCarrier
import modules.submissions.domain.services.SubmissionsJsonFormatters
import cats.data.NonEmptyList

class ThirdPartyApplicationSubmissionsConnectorSpec 
    extends BaseConnectorIntegrationSpec 
    with GuiceOneAppPerSuite 
    with WireMockExtensions 
    with CollaboratorTracker 
    with LocalUserIdTracker 
    with SubmissionsTestData
    with SubmissionsJsonFormatters {
  private val apiKey = UUID.randomUUID().toString

  private val stubConfig = Configuration(
    "microservice.services.third-party-application-production.port" -> stubPort,
    "microservice.services.third-party-application-production.use-proxy" -> false,
    "microservice.services.third-party-application-production.api-key" -> apiKey
  )
 
  override def fakeApplication(): PlayApplication =
    GuiceApplicationBuilder()
      .configure(stubConfig)
      .overrides(bind[ConnectorMetrics].to[NoopConnectorMetrics])
      .in(Mode.Test)
      .build()

  trait Setup {
    implicit val hc = HeaderCarrier()
    
    val connector = app.injector.instanceOf[ThirdPartyApplicationSubmissionsConnector]
  }
    
  "recordAnswer" should {
    val url = s"/submissions/${submissionId.value}/question/${questionId.value}"
    val answers = NonEmptyList.of("Yes")

    "return with an error" in new Setup {
      stubFor(
        post(urlEqualTo(url))
        .withJsonRequestBody(OutboundRecordAnswersRequest(answers))
        .willReturn(
          aResponse()
            .withStatus(INTERNAL_SERVER_ERROR)
        )
      )

      val result = await(connector.recordAnswer(submissionId, questionId, answers))

      result shouldBe 'Left
    }

    "return OK with and return the submission" in new Setup {
      stubFor(
        post(urlEqualTo(url))
        .withJsonRequestBody(OutboundRecordAnswersRequest(answers))
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withJsonBody(submission)
        )
      )

      val result = await(connector.recordAnswer(submissionId, questionId, answers))

      result shouldBe 'Right

      result.right.get shouldBe submission
    }
  }

  "fetchSubmission" should {
    val url = s"/submissions/${submissionId.value}"

    "return NOT FOUND with empty response body" in new Setup {
      stubFor(
        get(urlEqualTo(url))
        .willReturn(
            aResponse()
            .withStatus(NOT_FOUND)
        )
      )

      val result = await(connector.fetchSubmission(submissionId))

      result shouldBe None
    }

    "return OK with a submission body" in new Setup {
      stubFor(
        get(urlEqualTo(url))
        .willReturn(
            aResponse()
            .withStatus(OK)
            .withJsonBody(submission)
        )
      )

      val result = await(connector.fetchSubmission(submissionId))

      result.value shouldBe submission
    }
  }

  "fetchLatestSubmission" should {
    val url = s"/submissions/application/${applicationId.value}"

    "return NOT FOUND with empty response body" in new Setup {
      stubFor(
        get(urlEqualTo(url))
        .willReturn(
            aResponse()
            .withStatus(NOT_FOUND)
        )
      )

      val result = await(connector.fetchLatestSubmission(applicationId))

      result shouldBe None
    }

    "return OK with a submission body" in new Setup {
      stubFor(
        get(urlEqualTo(url))
        .willReturn(
            aResponse()
            .withStatus(OK)
            .withJsonBody(submission)
        )
      )

      val result = await(connector.fetchLatestSubmission(applicationId))

      result.value shouldBe submission
    }
  }
}
