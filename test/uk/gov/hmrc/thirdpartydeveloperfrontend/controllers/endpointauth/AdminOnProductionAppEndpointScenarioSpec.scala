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
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.endpointauth.preconditions.HasApplicationState
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.{ApplicationState, Collaborator, CollaboratorRole, Environment}

class AdminOnProductionAppEndpointScenarioSpec extends EndpointScenarioSpec
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
  def collaborators: Set[Collaborator] = Set(Collaborator(userEmail, CollaboratorRole.ADMINISTRATOR, userId))

  override def updateRequestForScenario[T](request: FakeRequest[T]): FakeRequest[T] = { //TODO this belongs inside the UserIsAuthenticated trait
    request.withCookies(
      Cookie("PLAY2AUTH_SESS_ID", cookieSigner.sign(sessionId) + sessionId, None, "path", None, false, false)
    ).withSession(
      ("email" , user.email),
      ("emailAddress" , user.email),
      ("nonce" , "123")
    )
  }

  override def describeScenario(): String = "User is authenticated as an Admin on the application team and the application is in the Production state"

  override def getExpectedResponse(endpoint: Endpoint): Response = {
    endpoint match {
      case Endpoint("GET", "/registration") => Redirect("/developer/applications")
      case Endpoint("POST", "/registration") => BadRequest()
      case Endpoint("GET",  "/reset-password/error") => BadRequest()
      case Endpoint("GET",  "/applications/add/production") => BadRequest()
      case Endpoint(_,      "/applications/add/switch") => BadRequest()
      case Endpoint("GET",  "/applications/add/:id") => BadRequest()
      case Endpoint("GET",  "/applications/:id/add/success") => NotFound()
      case Endpoint(_,      "/applications/:id/details/change") => Forbidden()
      case Endpoint("POST", "/applications/:id/change-subscription") => BadRequest()
      case Endpoint("GET",  "/applications/:id/request-check/appDetails") => getEndpointSuccessResponse(endpoint)
      case Endpoint("GET",  "/applications/:id/request-check/submitted") => getEndpointSuccessResponse(endpoint)
      case Endpoint(_, path) if path.startsWith("/applications/:id/request-check") => BadRequest()
      case Endpoint(_, path) if path.startsWith("/applications/:id/check-your-answers") => BadRequest()
      case Endpoint("POST", "/applications/:id/delete-subordinate") => Error("uk.gov.hmrc.http.ForbiddenException: Only standard subordinate applications can be deleted by admins")
      case _ => getEndpointSuccessResponse(endpoint)
    }
  }

}
