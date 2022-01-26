package uk.gov.hmrc.thirdpartydeveloperfrontend.connectors

import java.util.UUID
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils._
import play.api.Configuration
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.inject.bind
import play.api.Mode
import play.api.{Application => PlayApplication}
import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.http.Status._
import uk.gov.hmrc.apiplatform.modules.submissions.connectors.ThirdPartyApplicationSubmissionsConnector
import uk.gov.hmrc.apiplatform.modules.submissions.connectors.ThirdPartyApplicationSubmissionsConnector._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.apiplatform.modules.submissions.domain.services.SubmissionsFrontendJsonFormatters
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.services.ApplicationsJsonFormatters
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models.ErrorDetails

class ThirdPartyApplicationSubmissionsConnectorSpec 
    extends BaseConnectorIntegrationSpec 
    with GuiceOneAppPerSuite 
    with WireMockExtensions 
    with CollaboratorTracker 
    with LocalUserIdTracker 
    with SubmissionsTestData
    with SubmissionsFrontendJsonFormatters
    with TestApplications
    with ApplicationsJsonFormatters {
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
    val answers = List("Yes")

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
            .withJsonBody(extendedSubmission)
        )
      )

      val result = await(connector.recordAnswer(submissionId, questionId, answers))

      result shouldBe 'Right

      result.right.get shouldBe extendedSubmission
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
            .withJsonBody(extendedSubmission)
        )
      )

      val result = await(connector.fetchSubmission(submissionId))

      result.value shouldBe extendedSubmission
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
            .withJsonBody(extendedSubmission)
        )
      )

      val result = await(connector.fetchLatestSubmission(applicationId))

      result.value shouldBe extendedSubmission
    }
  }

  "requestApproval" should {
    val app = aStandardApplication
    val url = s"/application/${app.id.value}/request-approval"
    val email = "bob@spongepants.com"

    "return OK with and return the application" in new Setup {
      stubFor(
        post(urlEqualTo(url))
        .withJsonRequestBody(ApprovalsRequest(email))
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withJsonBody(app)
        )
      )

      val result = await(connector.requestApproval(app.id, email))

      result shouldBe 'Right
      result.right.get.name shouldBe app.name
    }

    "return with a PRECONDITION_FAILED error" in new Setup {
      val msg = "The application name contains words that are prohibited"

      stubFor(
        post(urlEqualTo(url))
        .withJsonRequestBody(ApprovalsRequest(email))
        .willReturn(
          aResponse()
            .withStatus(PRECONDITION_FAILED)
            .withJsonBody(ErrorDetails("code123", msg))
        )
      )

      val result = await(connector.requestApproval(app.id, email))

      result shouldBe 'Left
      result.left.get.message shouldBe msg
    }

    "return with a CONFLICT error" in new Setup {
      val msg = "An application already exists for the name"

      stubFor(
        post(urlEqualTo(url))
        .withJsonRequestBody(ApprovalsRequest(email))
        .willReturn(
          aResponse()
            .withStatus(CONFLICT)
            .withJsonBody(ErrorDetails("code123", msg))
        )
      )

      val result = await(connector.requestApproval(app.id, email))

      result shouldBe 'Left
      result.left.get.message shouldBe msg
    }

    "return with a BAD_REQUEST error" in new Setup {
      stubFor(
        post(urlEqualTo(url))
        .withJsonRequestBody(ApprovalsRequest(email))
        .willReturn(
          aResponse()
            .withStatus(BAD_REQUEST)
        )
      )

      intercept[RuntimeException] {
        await(connector.requestApproval(app.id, email))
      }
    }
  }
}
