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

import play.api.http.Status.OK
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{AnyContentAsEmpty, Request}
import play.api.test.{CSRFTokenHelper, FakeRequest}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.UserAuthenticationResponse
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.{Developer, LoggedInState, Session, UserId}
import play.api.mvc.{AnyContentAsEmpty, Cookie, Request}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.{Application, ApplicationId, ApplicationState, ApplicationWithSubscriptionIds, ClientId, Collaborator, CollaboratorRole, Environment, IpAllowlist, Standard}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.emailpreferences.EmailPreferences

import java.time.{LocalDateTime, Period}
import scala.concurrent.Future

class AdminOnAppEndpointScenarioSpec extends EndpointScenarioSpec {
  val sessionId = "my session"
  val user = Developer(
    UserId.random, "admin@example.com", "Bob", "Admin", None, List.empty, EmailPreferences.noPreferences
  )
  val session = Session(sessionId, user, LoggedInState.LOGGED_IN)

  implicit val cookieSigner: CookieSigner = app.injector.instanceOf[CookieSigner]

  when(tpdConnector.authenticate(*)(*)).thenReturn(Future.successful(UserAuthenticationResponse(false, false, None, Some(session))))
  when(tpdConnector.fetchSession(eqTo(sessionId))(*)).thenReturn(Future.successful(session))
  when(tpdConnector.deleteSession(eqTo(sessionId))(*)).thenReturn(Future.successful(OK))

  val access = Standard()
  val collaborators = Set(Collaborator(user.email, CollaboratorRole.ADMINISTRATOR, user.userId))
  val application = Application(
    ApplicationId.random, ClientId.random, "my app", LocalDateTime.now, None, None, Period.ofYears(1), Environment.PRODUCTION, None, collaborators, access,
    ApplicationState.production("mr requester", "code123"), None, IpAllowlist(false, Set.empty)
  )
  val appWithSubsIds = ApplicationWithSubscriptionIds.from(application)
  when(tpaProductionConnector.fetchByTeamMember(*[UserId])(*)).thenReturn(Future.successful(List(appWithSubsIds)))
  when(tpaSandboxConnector.fetchByTeamMember(*[UserId])(*)).thenReturn(Future.successful(List(appWithSubsIds)))

  override def buildRequest(httpVerb: String, requestPath: String): Request[AnyContentAsEmpty.type] = {
    CSRFTokenHelper.addCSRFToken(FakeRequest(httpVerb, requestPath).withCookies(
      Cookie("PLAY2AUTH_SESS_ID", cookieSigner.sign(sessionId) + sessionId, None, "path", None, false, false)
    ))
  }

  override def describeScenario(): String = "User is authenticated as an Admin on the application team"

  override def expectedEndpointResults(): EndpointResults = EndpointResults(
    Redirect("/developer/login"),
    EndpointResultOverride(Endpoint("GET", "/login"), Success()),
    EndpointResultOverride(Endpoint("GET", "/locked"), Success()),
    EndpointResultOverride(Endpoint("GET", "/forgot-password"), Success()),
    EndpointResultOverride(Endpoint("POST", "/support"), BadRequest()),
    EndpointResultOverride(Endpoint("GET", "/reset-password"), Redirect("/developer/reset-password/error"))
  )
}
