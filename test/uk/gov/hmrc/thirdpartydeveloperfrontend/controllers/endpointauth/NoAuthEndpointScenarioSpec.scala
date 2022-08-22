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
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.endpointauth.preconditions.{DeskproTicketCreationSucceeds, HasApplicationAccessStandard, NoUserIdFoundForEmailAddressValue, PasswordResetSucceeds, UserRegistrationSucceeds, UserVerificationSucceeds}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.{ApplicationState, Collaborator, CollaboratorRole, Environment}

class NoAuthEndpointScenarioSpec extends EndpointScenarioSpec
    with NoUserIdFoundForEmailAddressValue
    with DeskproTicketCreationSucceeds
    with UserVerificationSucceeds
    with PasswordResetSucceeds
    with HasApplicationAccessStandard
    with UserRegistrationSucceeds {

  def environment: Environment = Environment.PRODUCTION
  def applicationState: ApplicationState = ApplicationState.production("mr requester", "code123")
  def collaborators: Set[Collaborator] = Set(Collaborator(userEmail, CollaboratorRole.ADMINISTRATOR, userId))

  override def describeScenario(): String = "User is not authenticated"

  override def expectedResponses(): ExpectedResponses = ExpectedResponses(
    Redirect("/developer/login"),
    ExpectedResponseOverride(Endpoint("GET", "/login"), Success()),
    ExpectedResponseOverride(Endpoint("POST", "/login"), Unauthorized()),
    ExpectedResponseOverride(Endpoint("GET", "/registration"), Success()),
    ExpectedResponseOverride(Endpoint("POST", "/registration"), Redirect("/developer/confirmation")),
    ExpectedResponseOverride(Endpoint("GET", "/login-totp"), Success()),
    ExpectedResponseOverride(Endpoint("GET", "/application-verification"), Success()),
    //TODO the request below triggers a deskpro ticket to be created (should unauth users be able to do this?) that requests 2SV removal for any account, seems wrong? do SDST verify that the correct user made the request?
    ExpectedResponseOverride(Endpoint("GET", "/login/2SV-help"), Success()),
    ExpectedResponseOverride(Endpoint("POST", "/login/2SV-help"), Success()),
    ExpectedResponseOverride(Endpoint("GET", "/login/2SV-help/complete"), Success()),
    ExpectedResponseOverride(Endpoint("GET", "/confirmation"), Success()),
    ExpectedResponseOverride(Endpoint("GET", "/resend-confirmation"), Success()),
    ExpectedResponseOverride(Endpoint("POST", "/confirmation"), Success()),
    ExpectedResponseOverride(Endpoint("GET", "/support/submitted"), Success()),
    ExpectedResponseOverride(Endpoint("GET", "/verification"), Success()),
    ExpectedResponseOverride(Endpoint("GET", "/resend-verification"), BadRequest()),
    ExpectedResponseOverride(Endpoint("GET", "/locked"), Locked()),
    ExpectedResponseOverride(Endpoint("GET", "/reset-password"), Redirect("/developer/reset-password/error")),
    ExpectedResponseOverride(Endpoint("GET", "/forgot-password"), Success()),
    ExpectedResponseOverride(Endpoint("POST", "/forgot-password"), Success()),
    ExpectedResponseOverride(Endpoint("GET", "/user-navlinks"), Success()),
    ExpectedResponseOverride(Endpoint("GET", "/logout"), Success()),
    ExpectedResponseOverride(Endpoint("GET", "/partials/terms-of-use"), Success()),
    ExpectedResponseOverride(Endpoint("GET", "/reset-password-link"), Redirect("/developer/reset-password")),
    ExpectedResponseOverride(Endpoint("GET", "/reset-password/error"), BadRequest()),
    ExpectedResponseOverride(Endpoint("GET", "/support"), Success()),
    ExpectedResponseOverride(Endpoint("POST", "/support"), Redirect("/developer/support/submitted")),
    ExpectedResponseOverride(Endpoint("GET", "/assets/*file"), Success()),
    ExpectedResponseOverride(Endpoint("POST", "/reset-password"), Error("java.lang.RuntimeException: email not found in session")),
    ExpectedResponseOverride(Endpoint("POST", "/login-totp"), Error("java.util.NoSuchElementException: None.get"))
  )

}
