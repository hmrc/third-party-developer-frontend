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
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.ApplicationState

class AdminOnProductionAppEndpointScenarioSpec extends EndpointScenarioSpec
  with UserIsAuthenticated
  with UserIsAdminOnApplicationTeam
  with FlowRepoUpdateSucceeds
  with UserRegistrationFails
  with UserVerificationSucceeds
  with PasswordResetSucceeds
  with DeskproTicketCreationSucceeds
  with AddTeamMemberSucceeds
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
  )

}
