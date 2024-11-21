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
import play.api.{Application => PlayApplication, Configuration, Mode}
import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.applications.common.domain.models.FullName
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.{ApplicationName, ApplicationWithCollaboratorsFixtures}
import uk.gov.hmrc.apiplatform.modules.applications.submissions.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.domain.models.ApplicationId
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.submissions.SubmissionsTestData
import uk.gov.hmrc.apiplatform.modules.submissions.connectors.ThirdPartyApplicationSubmissionsConnector
import uk.gov.hmrc.apiplatform.modules.submissions.connectors.ThirdPartyApplicationSubmissionsConnector._
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models._
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.services.ApplicationsJsonFormatters
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils._

class ThirdPartyApplicationSubmissionsConnectorSpec
    extends BaseConnectorIntegrationSpec
    with GuiceOneAppPerSuite
    with WireMockExtensions
    with CollaboratorTracker
    with ApplicationWithCollaboratorsFixtures
    with LocalUserIdTracker
    with SubmissionsTestData
    with ApplicationsJsonFormatters {

  import Submission._

  private val apiKey = UUID.randomUUID().toString
  private val code   = "123456789"

  private val stubConfig = Configuration(
    "microservice.services.third-party-application-production.port"      -> stubPort,
    "microservice.services.third-party-application-production.use-proxy" -> false,
    "microservice.services.third-party-application-production.api-key"   -> apiKey
  )

  override def fakeApplication(): PlayApplication =
    GuiceApplicationBuilder()
      .configure(stubConfig)
      .overrides(bind[ConnectorMetrics].to[NoopConnectorMetrics])
      .in(Mode.Test)
      .build()

  trait Setup {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val connector = app.injector.instanceOf[ThirdPartyApplicationSubmissionsConnector]

    val riVerification: ResponsibleIndividualVerification = ResponsibleIndividualToUVerification(
      ResponsibleIndividualVerificationId(code),
      ApplicationId.random,
      SubmissionId.random,
      0,
      ApplicationName("App name"),
      instant,
      ResponsibleIndividualVerificationState.INITIAL
    )
    val responsibleIndividual                             = ResponsibleIndividual(FullName("bob example"), "bob@example.com".toLaxEmail)
    val riVerificationWithDetails                         = ResponsibleIndividualVerificationWithDetails(riVerification, responsibleIndividual, "Rick Deckard", "rick@submitter.com".toLaxEmail)

    val extendedSubmission = answeringSubmission.withIncompleteProgress()
  }

  "recordAnswer" should {
    val url     = s"/submissions/$submissionId/question/${questionId.value}"
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

      result.isLeft shouldBe true
    }

    "return OK with the submission" in new Setup {
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

      result.isRight shouldBe true
      result shouldBe Right(extendedSubmission)
    }
  }

  "fetchSubmission" should {
    val url = s"/submissions/$submissionId"

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
    val url = s"/submissions/application/${applicationId}"

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
              .withJsonBody(answeredSubmission)
          )
      )

      val result = await(connector.fetchLatestSubmission(applicationId))

      result.value shouldBe answeredSubmission
    }
  }

  "createSubmission" should {
    val app   = standardApp
    val email = "bob@example.com".toLaxEmail
    val url   = s"/submissions/application/${app.id.value}"

    "return successfully if TPA returns OK" in new Setup {
      stubFor(
        post(urlEqualTo(url))
          .withJsonRequestBody(CreateSubmissionRequest(email))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withJsonBody(createdSubmission)
          )
      )

      val result = await(connector.createSubmission(app.id, email))

      result.value shouldBe createdSubmission
    }
  }

  "fetchLatestExtendedSubmission" should {
    val url = s"/submissions/application/${applicationId}/extended"

    "return NOT FOUND with empty response body" in new Setup {
      stubFor(
        get(urlEqualTo(url))
          .willReturn(
            aResponse()
              .withStatus(NOT_FOUND)
          )
      )

      val result = await(connector.fetchLatestExtendedSubmission(applicationId))

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

      val result = await(connector.fetchLatestExtendedSubmission(applicationId))

      result.value shouldBe extendedSubmission
    }
  }

  "confirmSetupComplete" should {
    val app   = standardApp
    val email = "bob@example.com".toLaxEmail
    val url   = s"/application/${app.id.value}/confirm-setup-complete"

    "return successfully if TPA returns OK" in new Setup {
      stubFor(
        post(urlEqualTo(url))
          .withJsonRequestBody(ConfirmSetupCompleteRequest(email))
          .willReturn(
            aResponse()
              .withStatus(OK)
          )
      )

      val result = await(connector.confirmSetupComplete(app.id, email))

      result.isRight shouldBe true
    }

    "return an error if TPA returns error" in new Setup {
      stubFor(
        post(urlEqualTo(url))
          .withJsonRequestBody(ConfirmSetupCompleteRequest(email))
          .willReturn(
            aResponse()
              .withStatus(BAD_REQUEST)
          )
      )

      val result = await(connector.confirmSetupComplete(app.id, email))

      result shouldBe Left(s"Failed to confirm setup complete for application ${app.id.value}")
    }
  }

  "requestApproval" should {
    val app   = standardApp
    val url   = s"/approvals/application/${app.id.value}/request"
    val name  = "bob example"
    val email = "bob@spongepants.com".toLaxEmail

    "return application with OK" in new Setup {
      stubFor(
        post(urlEqualTo(url))
          .withJsonRequestBody(ApprovalsRequest(name, email))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withJsonBody(app)
          )
      )

      val result = await(connector.requestApproval(app.id, name, email))

      result.isRight shouldBe true
      result.toOption.value.name shouldBe app.name
    }

    "return with a PRECONDITION_FAILED error" in new Setup {
      val msg = "The application name contains words that are prohibited"

      stubFor(
        post(urlEqualTo(url))
          .withJsonRequestBody(ApprovalsRequest(name, email))
          .willReturn(
            aResponse()
              .withStatus(PRECONDITION_FAILED)
              .withJsonBody(ErrorDetails("code123", msg))
          )
      )

      val result = await(connector.requestApproval(app.id, name, email))

      result.isLeft shouldBe true
      result.left.toOption.get.message shouldBe msg
    }

    "return with a CONFLICT error" in new Setup {
      val msg = "An application already exists for the name"

      stubFor(
        post(urlEqualTo(url))
          .withJsonRequestBody(ApprovalsRequest(name, email))
          .willReturn(
            aResponse()
              .withStatus(CONFLICT)
              .withJsonBody(ErrorDetails("code123", msg))
          )
      )

      val result = await(connector.requestApproval(app.id, name, email))

      result.isLeft shouldBe true
      result.left.toOption.get.message shouldBe msg
    }

    "return with a BAD_REQUEST error" in new Setup {
      stubFor(
        post(urlEqualTo(url))
          .withJsonRequestBody(ApprovalsRequest(name, email))
          .willReturn(
            aResponse()
              .withStatus(BAD_REQUEST)
          )
      )

      intercept[RuntimeException] {
        await(connector.requestApproval(app.id, name, email))
      }
    }
  }

  "fetchResponsibleIndividualVerification" should {
    val url = s"/approvals/responsible-individual-verification/${code}"

    "return NOT FOUND with empty response body" in new Setup {
      stubFor(
        get(urlEqualTo(url))
          .willReturn(
            aResponse()
              .withStatus(NOT_FOUND)
          )
      )

      val result = await(connector.fetchResponsibleIndividualVerification(code))

      result shouldBe None
    }

    "return OK with a responsible individual verification body" in new Setup {
      stubFor(
        get(urlEqualTo(url))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withJsonBody(riVerification)
          )
      )

      val result = await(connector.fetchResponsibleIndividualVerification(code))

      result.value shouldBe riVerification
    }
  }
}
