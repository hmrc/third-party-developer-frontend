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
  with IsNewJourneyApplication
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

  override def getExpectedResponse(endpoint: Endpoint): Response = {
    endpoint match {
      case Endpoint("GET",  "/registration") => Redirect("/developer/applications")
      case Endpoint("POST", "/registration") => BadRequest()
      case Endpoint("GET",  "/reset-password/error") => BadRequest()
      case Endpoint("GET",  "/applications/add/production") => BadRequest()
      case Endpoint(_,      "/applications/add/switch") => BadRequest()
      case Endpoint("GET",  "/applications/add/:id") => BadRequest()
      case Endpoint("GET",  "/applications/:id/add/success") => NotFound()
      case Endpoint("POST", "/applications/:id/team-members/remove") => Forbidden()
      case Endpoint("POST", "/applications/:id/team-members/add/:addTeamMemberPageMode") => Forbidden()
      case Endpoint(_,      "/applications/:id/details/change") => Forbidden()
      case Endpoint(_,      "/applications/:id/details/change-privacy-policy-location") => Forbidden()
      case Endpoint(_,      "/applications/:id/details/change-terms-conditions-location") => Forbidden()
      case Endpoint(_,      "/applications/:id/details/terms-of-use") => Forbidden()
      case Endpoint("POST", "/applications/:id/delete-subordinate") => Forbidden()
      case Endpoint(_,      "/applications/:id/delete-subordinate-confirm") => Forbidden()
      case Endpoint("GET",  "/applications/:id/delete-principal-confirm") => Forbidden()
      case Endpoint("POST", "/applications/:id/delete-principal") => Forbidden()
      case Endpoint("POST", "/applications/:id/change-subscription") => BadRequest()
      case Endpoint("POST", "/applications/:id/client-secret-new") => Forbidden()
      case Endpoint("POST", "/applications/:id/client-secret/:clientSecretId/delete") => Forbidden()
      case Endpoint(_,      "/applications/:id/details/change-app-name") => Forbidden()
      case Endpoint(_,      "/applications/:id/change-private-subscription") => Forbidden()
      case Endpoint(_,      "/applications/:id/change-locked-subscription") => Forbidden()
      case Endpoint("GET",  "/applications/:id/client-id") => Forbidden()
      case Endpoint("GET",  "/applications/:id/server-token") => Forbidden()
      case Endpoint("GET",  "/applications/:id/client-secrets") => Forbidden()
      case Endpoint("GET",  "/applications/:id/client-secret/:clientSecretId/delete") => Forbidden()
      case Endpoint("GET",  "/applications/:id/push-secrets") => Forbidden()
      case Endpoint("GET", "/applications/:id/request-check/appDetails") => Success()
      case Endpoint("GET", "/applications/:id/request-check/submitted") => getEndpointSuccessResponse(endpoint)
      case Endpoint(_, path) if path.startsWith("/applications/:id/request-check") => BadRequest()
      case Endpoint(_, path) if path.startsWith("/applications/:id/check-your-answers") => BadRequest()
      case Endpoint(_, path) if path.startsWith("/applications/:id/responsible-individual/") => Forbidden()
      case Endpoint(_, path) if path.startsWith("/applications/:id/ip-allowlist/") => Forbidden()
      case Endpoint(_, path) if path.startsWith("/applications/:id/redirect-uris/") => Forbidden()

      case _ => getEndpointSuccessResponse(endpoint)
    }
  }

}
