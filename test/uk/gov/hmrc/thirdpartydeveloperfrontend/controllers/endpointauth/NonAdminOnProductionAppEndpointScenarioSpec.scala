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
import play.api.mvc.Cookie
import play.api.test.FakeRequest
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.endpointauth.preconditions._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.{ApplicationState, Collaborator, CollaboratorRole, Environment}

class NonAdminOnProductionAppEndpointScenarioSpec extends EndpointScenarioSpec
  with UserIsAuthenticated
  with UserIsOnApplicationTeam
  with ApplicationDetailsAreAvailable
  with FlowRepoUpdateSucceeds
  with UserRegistrationFails
  with UserVerificationSucceeds
  with PasswordResetSucceeds
  with DeskproTicketCreationSucceeds
  with ApplicationUpliftSucceeds
  with ApplicationNameIsValid
  with ApplicationUpdateSucceeds
  with HasApplicationAccessStandard
  with HasApplicationState {
  implicit val cookieSigner: CookieSigner = app.injector.instanceOf[CookieSigner]

  def environment: Environment = Environment.PRODUCTION
  def applicationState: ApplicationState = ApplicationState.production("mr requester", "code123")
  def collaborators: Set[Collaborator] = Set(Collaborator(userEmail, CollaboratorRole.DEVELOPER, userId))

  override def updateRequestForScenario[T](request: FakeRequest[T]): FakeRequest[T] = { //TODO this belongs inside the UserIsAuthenticated trait
    request.withCookies(
      Cookie("PLAY2AUTH_SESS_ID", cookieSigner.sign(sessionId) + sessionId, None, "path", None, false, false)
    ).withSession(
      ("email" , user.email),
      ("emailAddress" , user.email),
      ("nonce" , "123")
    )
  }

  override def describeScenario(): String = "User is authenticated as a non-admin on the application team and the application is in the Production state"

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
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/team-members/remove"), Forbidden()),
    ExpectedResponseOverride(Endpoint("GET", "/applications/:id/team-members/:teamMemberHash/remove-confirmation"), Redirect(s"/developer/applications/${applicationId.value}/team-members")),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/team-members/add/:addTeamMemberPageMode"), Forbidden()),
    ExpectedResponseOverride(Endpoint("GET", "/applications/:id/details/change"), Forbidden()),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/details/change"), Forbidden()),
    ExpectedResponseOverride(Endpoint("GET", "/applications/:id/details/change-privacy-policy-location"), Forbidden()),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/details/change-privacy-policy-location"), Forbidden()),
    ExpectedResponseOverride(Endpoint("GET", "/applications/:id/details/change-terms-conditions-location"), Forbidden()),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/details/change-terms-conditions-location"), Forbidden()),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/redirect-uris/delete-confirmation"), Forbidden()),
    ExpectedResponseOverride(Endpoint("GET", "/applications/:id/details/terms-of-use"), Forbidden()),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/details/terms-of-use"), Forbidden()),
    ExpectedResponseOverride(Endpoint("GET", "/applications/:id/redirect-uris/add"), Forbidden()),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/redirect-uris/add"), Forbidden()),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/redirect-uris/delete"), Forbidden()),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/redirect-uris/change"), Forbidden()),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/redirect-uris/change-confirmation"), Forbidden()),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/delete-subordinate"), Forbidden()),
    ExpectedResponseOverride(Endpoint("GET", "/applications/:id/delete-subordinate-confirm"), Forbidden()),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/delete-subordinate-confirm"), Forbidden()),
    ExpectedResponseOverride(Endpoint("GET", "/applications/:id/ip-allowlist/change"), Forbidden()),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/ip-allowlist/change"), Forbidden()),
    ExpectedResponseOverride(Endpoint("GET", "/applications/:id/ip-allowlist/add"), Forbidden()),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/ip-allowlist/add"), Forbidden()),
    ExpectedResponseOverride(Endpoint("GET", "/applications/:id/ip-allowlist/setup"), Forbidden()),
    ExpectedResponseOverride(Endpoint("GET", "/applications/:id/ip-allowlist/remove"), Forbidden()),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/ip-allowlist/remove"), Forbidden()),
    ExpectedResponseOverride(Endpoint("GET", "/applications/:id/responsible-individual/change/self-or-other"), Forbidden()),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/responsible-individual/change/self-or-other"), Forbidden()),
    ExpectedResponseOverride(Endpoint("GET", "/applications/:id/responsible-individual/change/self"), Forbidden()),
    ExpectedResponseOverride(Endpoint("GET", "/applications/:id/responsible-individual/change/other"), Forbidden()),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/responsible-individual/change/self"), Forbidden()),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/responsible-individual/change/other"), Forbidden()),
    ExpectedResponseOverride(Endpoint("GET", "/applications/:id/responsible-individual/change/self/confirmed"), Forbidden()),
    ExpectedResponseOverride(Endpoint("GET", "/applications/:id/responsible-individual/change/other/requested"), Forbidden()),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/change-subscription"), BadRequest()),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/client-secret-new"), Forbidden()),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/client-secret/:clientSecretId/delete"), Forbidden()),
    ExpectedResponseOverride(Endpoint("GET", "/applications/:id/request-check"), BadRequest()),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/request-check"), BadRequest()),
    ExpectedResponseOverride(Endpoint("GET", "/applications/:id/request-check/name"), BadRequest()),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/request-check/name"), BadRequest()),
    ExpectedResponseOverride(Endpoint("GET", "/applications/:id/request-check/contact"), BadRequest()),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/request-check/contact"), BadRequest()),
    ExpectedResponseOverride(Endpoint("GET", "/applications/:id/request-check/appDetails"), Success()),
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
    ExpectedResponseOverride(Endpoint("GET", "/applications/:id/details/change-app-name"), Forbidden()),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/details/change-app-name"), Forbidden()),
    ExpectedResponseOverride(Endpoint("GET", "/applications/:id/delete-principal-confirm"), Forbidden()),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/delete-principal"), Forbidden()),
    ExpectedResponseOverride(Endpoint("GET", "/applications/:id/ip-allowlist/allowed-ips"), Forbidden()),
    ExpectedResponseOverride(Endpoint("GET", "/applications/:id/ip-allowlist/deactivate"), Forbidden()),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/ip-allowlist/deactivate"), Forbidden()),
    ExpectedResponseOverride(Endpoint("GET", "/applications/:id/ip-allowlist/activate"), Forbidden()),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/ip-allowlist/activate"), Forbidden()),
    ExpectedResponseOverride(Endpoint("GET", "/applications/:id/change-private-subscription"), Forbidden()),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/change-private-subscription"), Forbidden()),
    ExpectedResponseOverride(Endpoint("GET", "/applications/:id/change-locked-subscription"), Forbidden()),
    ExpectedResponseOverride(Endpoint("POST", "/applications/:id/change-locked-subscription"), Forbidden()),
    ExpectedResponseOverride(Endpoint("GET", "/applications/:id/client-id"), Forbidden()),
    ExpectedResponseOverride(Endpoint("GET", "/applications/:id/server-token"), Forbidden()),
    ExpectedResponseOverride(Endpoint("GET", "/applications/:id/client-secrets"), Forbidden()),
    ExpectedResponseOverride(Endpoint("GET", "/applications/:id/client-secret/:clientSecretId/delete"), Forbidden()),
    ExpectedResponseOverride(Endpoint("GET", "/applications/:id/push-secrets"), Forbidden()),

  )

}
