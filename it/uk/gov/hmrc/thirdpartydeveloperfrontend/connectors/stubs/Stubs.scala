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

package uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.stubs

import java.net.URLEncoder

import com.github.tomakehurst.wiremock.client.WireMock._
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.EncryptedJson
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.{ApiContext, ApiIdentifier, ApiVersion}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.ApplicationNameValidationJson.ApplicationNameValidationResult
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.{Application, ApplicationToken, ClientId, Environment}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.UserAuthenticationResponse
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.{Registration, Session, UpdateProfileRequest}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.services.ApiDefinitionsJsonFormatters._

import play.api.http.Status._
import play.api.libs.json.{Json, Writes}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.PasswordResetRequest
import uk.gov.hmrc.apiplatform.modules.common.services.ApplicationLogger
import org.scalatest.matchers.should.Matchers

object Stubs extends ApplicationLogger {

  def setupRequest(path: String, status: Int, response: String) = {
    logger.info(s"Stubbing $path with $response")
    stubFor(
      get(urlEqualTo(path))
        .willReturn(aResponse().withStatus(status).withBody(response).withHeader("Content-type", "application/json"))
    )
  }

  def setupDeleteRequest(path: String, status: Int) =
    stubFor(delete(urlEqualTo(path)).willReturn(aResponse().withStatus(status)))

  def setupPostRequest(path: String, status: Int) =
    stubFor(post(urlEqualTo(path)).willReturn(aResponse().withStatus(status)))

  def setupPostRequest(path: String, status: Int, response: String) =
    stubFor(
      post(urlEqualTo(path))
        .willReturn(aResponse().withStatus(status).withBody(response))
    )

  def setupPostContaining(path: String, data: String, status: Int): Unit =
    stubFor(
      post(urlPathEqualTo(path))
        .withRequestBody(containing(data))
        .willReturn(aResponse().withStatus(status))
    )

  def setupEncryptedPostRequest[T](path: String, data: T, status: Int, response: String)(
      implicit writes: Writes[T],
      encryptedJson: EncryptedJson
  ) =
    stubFor(
      post(urlPathEqualTo(path))
        .withRequestBody(equalToJson(encryptedJson.toSecretRequestJson(data).toString()))
        .willReturn(aResponse().withStatus(status).withBody(response))
    )
}

object DeveloperStub {

  def register(registration: Registration, status: Int)(implicit encryptedJson: EncryptedJson) =
    stubFor(
      post(urlMatching(s"/developer"))
        .withRequestBody(equalToJson(encryptedJson.toSecretRequestJson(registration).toString()))
        .willReturn(aResponse().withStatus(status))
    )

  def update(email: String, profile: UpdateProfileRequest, status: Int) =
    stubFor(
      post(urlMatching(s"/developer/$email"))
        .withRequestBody(equalToJson(Json.toJson(profile).toString()))
        .willReturn(aResponse().withStatus(status))
    )

  def setupResend(email: String, status: Int) = {
    stubFor(
      post(urlPathEqualTo(s"/$email/resend-verification"))
        .willReturn(aResponse().withStatus(status))
    )
  }

  def verifyResetPassword(email: String, request: PasswordResetRequest) = {
    verify(1, postRequestedFor(urlPathEqualTo("/password-reset-request")).withRequestBody(matching(Json.toJson(request).toString())))
  }
}

object ApplicationStub {
  def setupApplicationNameValidation() = {
    val validNameResult = ApplicationNameValidationResult(None)

    Stubs.setupPostRequest("/application/name/validate", OK, Json.toJson(validNameResult).toString)
  }

  def setUpFetchApplication(id: String, status: Int, response: String = "") = {
    stubFor(
      get(urlEqualTo(s"/application/$id"))
        .willReturn(aResponse().withStatus(status).withBody(response))
    )
  }

  def setUpFetchEmptySubscriptions(id: String, status: Int) = {
    stubFor(
      get(urlEqualTo(s"/application/$id/subscription"))
        .willReturn(aResponse().withStatus(status).withBody("[]"))
    )
  }

  def setUpDeleteSubscription(id: String, api: String, version: ApiVersion, status: Int) = {
    stubFor(
      delete(urlEqualTo(s"/application/$id/subscription?context=$api&version=${version.value}"))
        .willReturn(aResponse().withStatus(status))
    )
  }

  def setUpExecuteSubscription(id: String, api: String, version: ApiVersion, status: Int) = {
    stubFor(
      post(urlEqualTo(s"/application/$id/subscription"))
        .withRequestBody(equalToJson(Json.toJson(ApiIdentifier(ApiContext(api), version)).toString()))
        .willReturn(aResponse().withStatus(status))
    )
  }

  def setUpUpdateApproval(id: String) = {
    stubFor(
      post(urlEqualTo(s"/application/$id/check-information"))
        .willReturn(aResponse().withStatus(OK))
    )
  }

  def configureUserApplications(email: String, applications: List[Application] = Nil, status: Int = OK) = {
    val encodedEmail = URLEncoder.encode(email, "UTF-8")

    def stubResponse(environment: Environment, applications: List[Application]) = {
      stubFor(
        get(urlPathEqualTo("/developer/applications"))
          .withQueryParam("emailAddress", equalTo(encodedEmail))
          .withQueryParam("environment", equalTo(environment.toString))
          .willReturn(
            aResponse()
              .withStatus(status)
              .withBody(Json.toJson(applications).toString())
          )
      )
    }

    val (prodApps, sandboxApps) = applications.partition(_.deployedTo == Environment.PRODUCTION)

    stubResponse(Environment.PRODUCTION, prodApps)
    stubResponse(Environment.SANDBOX, sandboxApps)
  }

  def configureApplicationCredentials(tokens: Map[String, ApplicationToken], status: Int = OK) = {
    tokens.foreach { entry =>
      stubFor(
        get(urlEqualTo(s"/application/${entry._1}/credentials"))
          .willReturn(
            aResponse()
              .withStatus(status)
              .withBody(Json.toJson(entry._2).toString())
              .withHeader("content-type", "application/json")
          )
      )
    }
  }
}

object DeskproStub extends Matchers {
  val deskproPath: String = "/deskpro/ticket"
  val deskproFeedbackPath: String = "/deskpro/feedback"

  def setupTicketCreation(status: Int = OK) = {
    Stubs.setupPostRequest(deskproPath, status)
  }

  def verifyTicketCreationWithSubject(subject: String) = {
    verify(1, postRequestedFor(urlPathEqualTo(deskproPath)).withRequestBody(containing(s""""subject":"$subject"""")))
  }
}

object AuditStub extends Matchers {
  val auditPath: String = "/write/audit"
  val mergedAuditPath: String = "/write/audit/merged"

  def setupAudit(status: Int = NO_CONTENT, data: Option[String] = None) = {
    if (data.isDefined) {
      Stubs.setupPostContaining(auditPath, data.get, status)
      Stubs.setupPostContaining(mergedAuditPath, data.get, status)
    } else {
      Stubs.setupPostRequest(auditPath, status)
      Stubs.setupPostRequest(mergedAuditPath, status)
    }
  }
}

object ThirdPartyDeveloperStub {
  def configureAuthenticate(session: Option[Session]) = {
    stubFor(
      post(urlEqualTo("/authenticate"))
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withBody(Json.toJson(UserAuthenticationResponse(false, None, session)).toString())
            .withHeader("content-type", "application/json")
        )
    )
  }
}

object ApiSubscriptionFieldsStub {

  def setUpDeleteSubscriptionFields(clientId: ClientId, apiContext: ApiContext, apiVersion: ApiVersion) = {
    stubFor(
      delete(urlEqualTo(fieldValuesUrl(clientId, apiContext, apiVersion)))
        .willReturn(aResponse().withStatus(NO_CONTENT))
    )
  }

  private def fieldValuesUrl(clientId: ClientId, apiContext: ApiContext, apiVersion: ApiVersion) = {
    s"/field/application/${clientId.value}/context/${apiContext.value}/version/${apiVersion.value}"
  }

  def noSubscriptionFields(apiContext: ApiContext, version: ApiVersion): Any = {
    stubFor(get(urlEqualTo(fieldDefinitionsUrl(apiContext, version))).willReturn(aResponse().withStatus(NOT_FOUND)))
  }

  private def fieldDefinitionsUrl(apiContext: ApiContext, version: ApiVersion) = {
    s"/definition/context/${apiContext.value}/version/${version.value}"
  }
}
