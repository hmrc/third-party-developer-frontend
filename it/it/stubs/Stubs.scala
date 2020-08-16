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

// TODO remove duplication

package it.stubs

import java.net.URLEncoder

import com.github.tomakehurst.wiremock.client.WireMock._
import connectors.EncryptedJson
import domain.models.applications.ApplicationNameValidationJson.ApplicationNameValidationResult
import domain.models.apidefinitions.DefinitionFormats._
import domain.models.apidefinitions.{ApiIdentifier, ApiContext, ApiVersion}
import domain.models.applications.{Application, ApplicationToken, Environment}
import domain.models.connectors.UserAuthenticationResponse
import domain.models.developers.{Registration, Session, UpdateProfileRequest}
import domain.models.subscriptions.APISubscription
import org.scalatest.Matchers
import play.api.Logger
import play.api.libs.json.{Json, Writes}
import play.api.http.Status._

object Stubs {

  def setupRequest(path: ClientId, status: Int, response: ClientId) = {
    Logger.info(s"Stubbing $path with $response")
    stubFor(
      get(urlEqualTo(path))
        .willReturn(aResponse().withStatus(status).withBody(response).withHeader("Content-type", "application/json"))
    )
  }

  def setupDeleteRequest(path: ClientId, status: Int) =
    stubFor(delete(urlEqualTo(path)).willReturn(aResponse().withStatus(status)))

  def setupPostRequest(path: ClientId, status: Int) =
    stubFor(post(urlEqualTo(path)).willReturn(aResponse().withStatus(status)))

  def setupPostRequest(path: ClientId, status: Int, response: ClientId) =
    stubFor(
      post(urlEqualTo(path))
        .willReturn(aResponse().withStatus(status).withBody(response))
    )

  def setupPostContaining(path: ClientId, data: ClientId, status: Int): Unit =
    stubFor(
      post(urlPathEqualTo(path))
        .withRequestBody(containing(data))
        .willReturn(aResponse().withStatus(status))
    )

  def setupEncryptedPostRequest[T](path: ClientId, data: T, status: Int, response: ClientId)(
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

  def update(email: ClientId, profile: UpdateProfileRequest, status: Int) =
    stubFor(
      post(urlMatching(s"/developer/$email"))
        .withRequestBody(equalToJson(Json.toJson(profile).toString()))
        .willReturn(aResponse().withStatus(status))
    )

  def setupResend(email: ClientId, status: Int) = {
    stubFor(
      post(urlPathEqualTo(s"/$email/resend-verification"))
        .willReturn(aResponse().withStatus(status))
    )
  }

  def verifyResetPassword(email: ClientId) = {
    verify(1, postRequestedFor(urlPathEqualTo(s"/$email/password-reset-request")))
  }
}

object ApplicationStub {
  def setupApplicationNameValidation() = {
    val validNameResult = ApplicationNameValidationResult(None)

    Stubs.setupPostRequest("/application/name/validate", OK, Json.toJson(validNameResult).toString)
  }

  def setUpFetchApplication(id: ClientId, status: Int, response: ClientId = "") = {
    stubFor(
      get(urlEqualTo(s"/application/$id"))
        .willReturn(aResponse().withStatus(status).withBody(response))
    )
  }

  def setUpFetchEmptySubscriptions(id: ClientId, status: Int) = {
    stubFor(
      get(urlEqualTo(s"/application/$id/subscription"))
        .willReturn(aResponse().withStatus(status).withBody("[]"))
    )
  }

  def setUpFetchSubscriptions(id: ClientId, status: Int, response: Seq[APISubscription]) = {
    stubFor(
      get(urlEqualTo(s"/application/$id/subscription"))
        .willReturn(aResponse().withStatus(status).withBody(Json.toJson(response).toString()))
    )
  }

  def setUpDeleteSubscription(id: ClientId, api: ClientId, version: ApiVersion, status: Int) = {
    stubFor(
      delete(urlEqualTo(s"/application/$id/subscription?context=$api&version=${version.value}"))
        .willReturn(aResponse().withStatus(status))
    )
  }

  def setUpExecuteSubscription(id: ClientId, api: ClientId, version: ApiVersion, status: Int) = {
    stubFor(
      post(urlEqualTo(s"/application/$id/subscription"))
        .withRequestBody(equalToJson(Json.toJson(ApiIdentifier(ApiContext(api), version)).toString()))
        .willReturn(aResponse().withStatus(status))
    )
  }

  def setUpUpdateApproval(id: ClientId) = {
    stubFor(
      post(urlEqualTo(s"/application/$id/check-information"))
        .willReturn(aResponse().withStatus(OK))
    )
  }

  def configureUserApplications(email: ClientId, applications: List[Application] = Nil, status: Int = OK) = {
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

  def configureApplicationCredentials(tokens: Map[ClientId, ApplicationToken], status: Int = OK) = {
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
  val deskproPath: ClientId = "/deskpro/ticket"
  val deskproFeedbackPath: ClientId = "/deskpro/feedback"

  def setupTicketCreation(status: Int = OK) = {
    Stubs.setupPostRequest(deskproPath, status)
  }

  def verifyTicketCreationWithSubject(subject: ClientId) = {
    verify(1, postRequestedFor(urlPathEqualTo(deskproPath)).withRequestBody(containing(s""""subject":"$subject"""")))
  }
}

object AuditStub extends Matchers {
  val auditPath: ClientId = "/write/audit"
  val mergedAuditPath: ClientId = "/write/audit/merged"

  def setupAudit(status: Int = NO_CONTENT, data: Option[ClientId] = None) = {
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
