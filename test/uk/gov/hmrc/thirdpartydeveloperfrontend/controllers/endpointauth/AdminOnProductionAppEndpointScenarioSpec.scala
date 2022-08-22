/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.endpointauth

import play.api.libs.crypto.CookieSigner
import play.api.mvc.{AnyContent, AnyContentAsEmpty, Cookie, Request, Session}
import play.api.test.{CSRFTokenHelper, FakeRequest}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.endpointauth.preconditions._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.{ApplicationId, ApplicationState}

class AdminOnProductionAppEndpointScenarioSpec extends EndpointScenarioSpec
  with UserIsAuthenticated
  with UserIsAdminOnApplicationTeam
  with FlowRepoUpdateSucceeds
  with UserRegistrationFails
  with UserVerificationSucceeds
  with PasswordResetSucceeds
  with DeskproTicketCreationSucceeds
  with AddTeamMemberSucceeds
  with ApplicationNameIsValid
  with ApplicationUpdateSucceeds
  with ApplicationHasState {
  implicit val cookieSigner: CookieSigner = app.injector.instanceOf[CookieSigner]

  def applicationState: ApplicationState = ApplicationState.production("mr requester", "code123")

  override def updateRequestForScenario[T](request: FakeRequest[T]): FakeRequest[T] = { //TODO this belongs inside the UserIsAuthenticated trait
    request.withCookies(
      Cookie("PLAY2AUTH_SESS_ID", cookieSigner.sign(sessionId) + sessionId, None, "path", None, false, false)
    ).withSession(
      ("email" , user.email),
      ("emailAddress" , user.email),
      ("nonce" , "123")
    )
  }

  override def getBodyParameterValueOverrides(endpoint: Endpoint): Map[String, String] = {
    endpoint match {
      case Endpoint("POST", "/applications/:id/check-your-answers/terms-and-conditions") => Map("hasUrl"-> "false")
      case Endpoint("POST", "/applications/:id/team-members/add/:addTeamMemberPageMode") => Map("email"-> "new@example.com", "role" -> "developer")
      case Endpoint("POST", "/applications/:id/team-members/remove") => Map("email"-> "new@example.com", "confirm" -> "yes")
      case Endpoint("POST", "/applications/:id/details/change-app-name") => Map("applicationName"-> "new app name")
      case Endpoint("POST", "/applications/:id/details/change-privacy-policy-location") => Map("privacyPolicyUrl" -> "http://example.com", "isInDesktop" -> "false", "isNewJourney" -> "true")
      case Endpoint("POST", "/applications/:id/details/change-terms-conditions-location") => Map("termsAndConditionsUrl" -> "http://example.com", "isInDesktop" -> "false", "isNewJourney" -> "true")
      case Endpoint("POST", "/applications/:id/redirect-uris/add") => Map("redirectUri" -> "https://example.com/redirect")
      case Endpoint("POST", "/applications/:id/details/terms-of-use") => Map("termsOfUseAgreed" -> "true")
      case Endpoint("POST", "/applications/:id/redirect-uris/change-confirmation") => Map("originalRedirectUri" -> "http://example.com", "newRedirectUri" -> "https://example.com/redirect")
      case Endpoint("POST", "/applications/:id/redirect-uris/delete") => Map("redirectUri" -> "http://example.com", "deleteRedirectConfirm" -> "yes")
      case Endpoint("POST", "/applications/:id/delete-principal") => Map("deleteConfirm" -> "yes")
      case Endpoint("POST", "/applications/:id/ip-allowlist/add") => Map("ipAddress" -> "1.2.3.4/24")
      case Endpoint("POST", "/applications/:id/ip-allowlist/change") => Map("confirm" -> "yes")
      case Endpoint("POST", "/applications/:id/responsible-individual/change/self-or-other") => Map("who" -> "self")
      case Endpoint("POST", "/applications/:id/responsible-individual/change/other") => Map("name" -> "mr responsible", "email" -> "ri@example.com")
      case Endpoint("POST", "/applications/:id/change-subscription") => Map("subscribed" -> "true")
      case Endpoint("POST", "/applications/:id/change-locked-subscription") => Map("subscribed" -> "true", "confirm" -> "true")
      case Endpoint("POST", "/applications/:id/change-private-subscription") => Map("subscribed" -> "true", "confirm" -> "true")
      case Endpoint("POST", "/applications/:id/add/subscription-configuration/:pageNumber") => Map("my_field" -> "my value")
      case Endpoint("POST", "/applications/:id/api-metadata/:context/:version/:saveSubsFieldsPageMode") => Map("my_field" -> "my value")
      case Endpoint("POST", "/applications/:id/api-metadata/:context/:version/fields/:fieldName/:saveSubsFieldsPageMode") => Map("my_field" -> "my value")
      case Endpoint("POST", "/no-applications") => Map("choice" -> "use-apis")
      case Endpoint("POST", "/applications/:id/change-api-subscriptions") => Map("ctx-1_0-subscribed" -> "true")
      case Endpoint("POST", "/applications/:id/sell-resell-or-distribute-your-software") => Map("answer" -> "yes")
      case _ => Map.empty
    }
  }

  override def describeScenario(): String = "User is authenticated as an Admin on the application team and the application is in the Production state"

  override def expectedResponses(): ExpectedResponses = ExpectedResponses(
    Success(),
    ExpectedResponseOverride(Endpoint("GET", "/registration"), Redirect("/developer/applications")),
    ExpectedResponseOverride(Endpoint("POST", "/registration"), BadRequest()),
    ExpectedResponseOverride(Endpoint("GET", "/resend-verification"), Redirect("/developer/confirmation")),
    ExpectedResponseOverride(Endpoint("GET", "/login"), Redirect("/developer/applications")),
    ExpectedResponseOverride(Endpoint("POST", "/login"), Redirect("/developer/login/2sv-recommendation")),
    ExpectedResponseOverride(Endpoint("GET", "/login/2SV-help"), Redirect("/developer/applications")),
    ExpectedResponseOverride(Endpoint("POST", "/login/2SV-help"), Redirect("/developer/applications")),
    ExpectedResponseOverride(Endpoint("GET", "/login/2SV-help/complete"), Redirect("/developer/applications")),
    ExpectedResponseOverride(Endpoint("POST", "/login-totp"), Redirect("/developer/applications")),
    ExpectedResponseOverride(Endpoint("POST", "/logout/survey"), Redirect("/developer/logout")),
    ExpectedResponseOverride(Endpoint("GET", "/locked"), Locked()),
    ExpectedResponseOverride(Endpoint("GET", "/forgot-password"), Redirect("/developer/applications")),
    ExpectedResponseOverride(Endpoint("GET", "/reset-password-link"), Redirect("/developer/reset-password")),
    ExpectedResponseOverride(Endpoint("POST", "/support"), Redirect("/developer/support/submitted")),
    ExpectedResponseOverride(Endpoint("GET", "/reset-password/error"), BadRequest()),
    ExpectedResponseOverride(Endpoint("GET", "/applications/add/production"), BadRequest()),
    ExpectedResponseOverride(Endpoint("GET", "/applications/add/switch"), BadRequest()),
    ExpectedResponseOverride(Endpoint("POST", "/applications/add/switch"), BadRequest()),
    ExpectedResponseOverride(Endpoint("GET", "/applications/add/:id"), BadRequest()),
    ExpectedResponseOverride(Endpoint("GET", "/applications/:id/add/success"), NotFound()),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/team-members/remove"), Redirect(s"/developer/applications/${applicationId.value}/team-members")),
    ExpectedResponseOverride(Endpoint("GET", "/applications/:id/team-members/:teamMemberHash/remove-confirmation"), Redirect(s"/developer/applications/${applicationId.value}/team-members")),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/team-members/add/:addTeamMemberPageMode"), Redirect(s"/developer/applications/${applicationId.value}/request-check/team")),
    ExpectedResponseOverride(Endpoint("GET", "/applications/:id/details/change"), Forbidden()),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/details/change"), Forbidden()),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/details/change-privacy-policy-location"), Redirect(s"/developer/applications/${applicationId.value}/details")),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/details/change-terms-conditions-location"), Redirect(s"/developer/applications/${applicationId.value}/details")),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/redirect-uris/delete-confirmation"), Redirect(s"/developer/applications/${applicationId.value}/redirect-uris")),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/details/terms-of-use"), Redirect(s"/developer/applications/${applicationId.value}/details")),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/redirect-uris/add"), Redirect(s"/developer/applications/${applicationId.value}/redirect-uris")),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/redirect-uris/delete"), Redirect(s"/developer/applications/${applicationId.value}/redirect-uris")),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/redirect-uris/change-confirmation"), Redirect(s"/developer/applications/${applicationId.value}/redirect-uris")),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/delete-subordinate"), Error("uk.gov.hmrc.http.ForbiddenException: Only standard subordinate applications can be deleted by admins")),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/delete-principal"), Redirect(s"/developer/applications/${applicationId.value}/details")),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/ip-allowlist/change"), Redirect(s"/developer/applications/${applicationId.value}/ip-allowlist/activate")),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/ip-allowlist/add"), Redirect(s"/developer/applications/${applicationId.value}/ip-allowlist/change")),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/ip-allowlist/remove"), Redirect(s"/developer/applications/${applicationId.value}/ip-allowlist/setup")),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/responsible-individual/change/self-or-other"), Redirect(s"/developer/applications/${applicationId.value}/responsible-individual/change/self")),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/responsible-individual/change/self"), Redirect(s"/developer/applications/${applicationId.value}/responsible-individual/change/self/confirmed")),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/responsible-individual/change/other"), Redirect(s"/developer/applications/${applicationId.value}/responsible-individual/change/other/requested")),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/change-subscription"), BadRequest()),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/client-secret-new"), Redirect(s"/developer/applications/${applicationId.value}/client-secrets")),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/client-secret/:clientSecretId/delete"), Redirect(s"/developer/applications/${applicationId.value}/client-secrets")),
    ExpectedResponseOverride(Endpoint("GET", "/applications/:id/request-check"), BadRequest()),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/request-check"), BadRequest()),
    ExpectedResponseOverride(Endpoint("GET", "/applications/:id/request-check/name"), BadRequest()),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/request-check/name"), BadRequest()),
    ExpectedResponseOverride(Endpoint("GET", "/applications/:id/request-check/contact"), BadRequest()),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/request-check/contact"), BadRequest()),
    ExpectedResponseOverride(Endpoint("GET", "/applications/:id/request-check/appDetails"), Redirect(s"/developer/applications/${applicationId.value}/request-check")),
    ExpectedResponseOverride(Endpoint("GET", "/applications/:id/request-check/subscriptions"), BadRequest()),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/request-check/subscriptions"), BadRequest()),
    ExpectedResponseOverride(Endpoint("GET", "/applications/:id/request-check/privacy-policy"), BadRequest()),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/request-check/privacy-policy"), BadRequest()),
    ExpectedResponseOverride(Endpoint("GET", "/applications/:id/request-check/terms-and-conditions"), BadRequest()),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/request-check/terms-and-conditions"), BadRequest()),
    ExpectedResponseOverride(Endpoint("GET", "/applications/:id/request-check/terms-of-use"), BadRequest()),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/request-check/terms-of-use"), BadRequest()),
    ExpectedResponseOverride(Endpoint("GET", "/applications/:id/request-check/team"), BadRequest()),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/request-check/team"), BadRequest()),
    ExpectedResponseOverride(Endpoint("GET", "/applications/:id/request-check/team/add"), BadRequest()),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/request-check/team/remove"), BadRequest()),
    ExpectedResponseOverride(Endpoint("GET", "/applications/:id/request-check/team/remove-confirmation/:teamMemberHash"), BadRequest()),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/add/subscription-configuration/:pageNumber"), Redirect(s"/developer/applications/${applicationId.value}/add/subscription-configuration-step/1")),
    ExpectedResponseOverride(Endpoint("GET", "/applications/:id/add/subscription-configuration-step/:pageNumber"), Redirect(s"/developer/applications/${applicationId.value}/request-check")),
    ExpectedResponseOverride(Endpoint("GET", "/applications/:id/check-your-answers"), BadRequest()),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/check-your-answers"), BadRequest()),
    ExpectedResponseOverride(Endpoint("GET", "/applications/:id/check-your-answers/name"), BadRequest()),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/check-your-answers/name"), BadRequest()),
    ExpectedResponseOverride(Endpoint("GET", "/applications/:id/check-your-answers/subscriptions"), BadRequest()),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/check-your-answers/subscriptions"), BadRequest()),
    ExpectedResponseOverride(Endpoint("GET", "/applications/:id/check-your-answers/privacy-policy"), BadRequest()),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/check-your-answers/privacy-policy"), BadRequest()),
    ExpectedResponseOverride(Endpoint("GET", "/applications/:id/check-your-answers/contact"), BadRequest()),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/check-your-answers/contact"), BadRequest()),
    ExpectedResponseOverride(Endpoint("GET", "/applications/:id/check-your-answers/terms-of-use"), BadRequest()),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/check-your-answers/terms-of-use"), BadRequest()),
    ExpectedResponseOverride(Endpoint("GET", "/applications/:id/check-your-answers/terms-and-conditions"), BadRequest()),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/check-your-answers/terms-and-conditions"), BadRequest()),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/check-your-answers/team/remove"), BadRequest()),
    ExpectedResponseOverride(Endpoint("GET", "/applications/:id/check-your-answers/team/add"), BadRequest()),
    ExpectedResponseOverride(Endpoint("GET", "/applications/:id/check-your-answers/team"), BadRequest()),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/check-your-answers/team"), BadRequest()),
    ExpectedResponseOverride(Endpoint("GET", "/applications/:id/check-your-answers/team/remove-confirmation/:teamMemberHash"), BadRequest()),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/api-metadata/:context/:version/:saveSubsFieldsPageMode"), Redirect(s"/developer/applications/${applicationId.value}/api-metadata")),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/api-metadata/:context/:version/fields/:fieldName/:saveSubsFieldsPageMode"), Redirect(s"/developer/applications/${applicationId.value}/api-metadata")),
    ExpectedResponseOverride(Endpoint("POST", "/no-applications"), Redirect(s"/developer/no-applications-start")),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/confirm-subscriptions"), Redirect(s"/developer/submissions/application/${applicationId.value}/production-credentials-checklist")),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/change-api-subscriptions"), Redirect(s"/developer/applications/${applicationId.value}/confirm-subscriptions")),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/sell-resell-or-distribute-your-software"), Redirect(s"/developer/applications/${applicationId.value}/confirm-subscriptions")),
  )

}
