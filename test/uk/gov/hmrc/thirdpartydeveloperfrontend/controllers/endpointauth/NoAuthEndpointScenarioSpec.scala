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
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.endpointauth.preconditions.{DeskproTicketCreationSucceeds, HasApplicationAccessStandardWithSubmissionData, IsNewJourneyApplication, NoUserIdFoundForEmailAddressValue, PasswordResetSucceeds, UserRegistrationSucceeds, UserVerificationSucceeds}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.{ApplicationState, Collaborator, CollaboratorRole, Environment}

class NoAuthEndpointScenarioSpec extends EndpointScenarioSpec
    with IsNewJourneyApplication
    with NoUserIdFoundForEmailAddressValue
    with DeskproTicketCreationSucceeds
    with UserVerificationSucceeds
    with PasswordResetSucceeds
    with UserRegistrationSucceeds {

  def environment: Environment = Environment.PRODUCTION
  def applicationState: ApplicationState = ApplicationState.production("mr requester", "code123")
  def collaborators: Set[Collaborator] = Set(Collaborator(userEmail, CollaboratorRole.ADMINISTRATOR, userId))

  override def describeScenario(): String = "User is not authenticated"

  override def getExpectedResponse(endpoint: Endpoint): Response = {
    endpoint match {
      case Endpoint("GET",  "/login") => Success()
      case Endpoint("POST", "/login") => Unauthorized()
      case Endpoint(_,      "/registration") => getEndpointSuccessResponse(endpoint)
      case Endpoint("GET",  "/login-totp") => Success()
      case Endpoint("POST", "/login-totp") => Error("java.util.NoSuchElementException: None.get")
      case Endpoint("GET",  "/application-verification") => Success()
      //TODO the request below triggers a deskpro ticket to be created (should unauth users be able to do this?) that requests 2SV removal for any account, seems wrong? do SDST verify that the correct user made the request?
      case Endpoint(_,      "/login/2SV-help") => Success()
      case Endpoint("GET",  "/login/2SV-help/complete") => Success()
      case Endpoint(_,      "/confirmation") => Success()
      case Endpoint("GET",  "/resend-confirmation") => Success()
      case Endpoint("GET",  "/support/submitted") => Success()
      case Endpoint("GET",  "/verification") => Success()
      case Endpoint("GET",  "/resend-verification") => BadRequest()
      case Endpoint("GET",  "/locked") => Locked()
      case Endpoint("GET",  "/reset-password") => Redirect("/developer/reset-password/error")
      case Endpoint("POST", "/reset-password") => Error("java.lang.RuntimeException: email not found in session")
      case Endpoint("GET",  "/reset-password-link") => Redirect("/developer/reset-password")
      case Endpoint("GET",  "/reset-password/error") => BadRequest()
      case Endpoint(_,      "/forgot-password") => Success()
      case Endpoint("GET",  "/user-navlinks") => Success()
      case Endpoint("GET",  "/logout") => Success()
      case Endpoint("GET",  "/partials/terms-of-use") => Success()
      case Endpoint(_,      "/support") => getEndpointSuccessResponse(endpoint)
      case Endpoint("GET",  "/assets/*file") => Success()
      case _ => Redirect("/developer/login")
    }
  }

}
