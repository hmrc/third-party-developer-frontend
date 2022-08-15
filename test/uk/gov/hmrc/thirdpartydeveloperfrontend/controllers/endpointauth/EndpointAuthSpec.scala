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

import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Mode
import play.api.http.Status.OK
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{AnyContentAsEmpty, Cookie, Request}
import play.api.test.Helpers.{redirectLocation, route, status}
import play.api.test.{CSRFTokenHelper, FakeRequest, Writeables}
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.DeveloperBuilder
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.UserAuthenticationResponse
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.{LoggedInState, Session, UserId}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{AsyncHmrcSpec, UserIdTracker}

import java.time.{LocalDateTime, Period}
import scala.concurrent.Future
import scala.io.Source

object EndpointAuthSpec {
  def parseEndpoint(text: String): Option[Endpoint] = {
    text.trim.split("\\s+") match {
      case Array(verb, path, _) => Some(Endpoint(verb, path))
      case _ => None
    }
  }
  case class Endpoint(verb: String, path: String)

  sealed trait EndpointResult
  case class Success() extends EndpointResult
  case class Redirect(location: String) extends EndpointResult
  case class BadRequest() extends EndpointResult
  case class Error(e: Exception) extends EndpointResult
  case class Unexpected(status: Int) extends EndpointResult

  sealed trait AuthCondition
  case object NoAuth extends AuthCondition
  case object LoggedInDeveloper extends AuthCondition

  case class Rule(authCondition: AuthCondition, result: EndpointResult)
  val NoAuthAllowed = Rule(NoAuth, Success())
  case class EndpointAccessRules(endpoint: Endpoint, rules: Rule*)

}

class MockConnectors extends AsyncHmrcSpec with DeveloperBuilder with UserIdTracker  {
  val tpdConnector: ThirdPartyDeveloperConnector = mock[ThirdPartyDeveloperConnector]
  val tpaProductionConnector: ThirdPartyApplicationProductionConnector = mock[ThirdPartyApplicationProductionConnector]
  val tpaSandboxConnector: ThirdPartyApplicationSandboxConnector = mock[ThirdPartyApplicationSandboxConnector]

  val sessionId = "my session"
  private val developer = buildDeveloper()
  private val session = Session(sessionId, developer, LoggedInState.LOGGED_IN)

  def idOf(email: String) = UserId.random

  def userAuthenticatesSuccessfully() = {
    when(tpdConnector.authenticate(*)(*)).thenReturn(Future.successful(
      UserAuthenticationResponse(false, false, None, Some(session))
    ))
    when(tpdConnector.fetchSession(eqTo(sessionId))(*)).thenReturn(Future.successful(
      session
    ))
    when(tpdConnector.deleteSession(eqTo(sessionId))(*)).thenReturn(Future.successful(OK))
  }

  def userIsTeamMember() = {
    val access = Standard()
    val collaborators = Set(Collaborator(developer.email, CollaboratorRole.ADMINISTRATOR, developer.userId))
    val application = Application(
      ApplicationId.random, ClientId.random, "my app", LocalDateTime.now, None, None, Period.ofYears(1), Environment.PRODUCTION, None, collaborators, access,
      ApplicationState.production("mr requester", "code123"), None, IpAllowlist(false, Set.empty)
    )
    val appWithSubsIds = ApplicationWithSubscriptionIds.from(application)
    when(tpaProductionConnector.fetchByTeamMember(*[UserId])(*)).thenReturn(Future.successful(List(appWithSubsIds)))
    when(tpaSandboxConnector.fetchByTeamMember(*[UserId])(*)).thenReturn(Future.successful(List(appWithSubsIds)))
  }
}

class EndpointAuthSpec extends AsyncHmrcSpec with GuiceOneAppPerSuite with Writeables {
  import EndpointAuthSpec._

  implicit val cookieSigner: CookieSigner = app.injector.instanceOf[CookieSigner]

  lazy val connectors = new MockConnectors()

  override def fakeApplication() = {
    GuiceApplicationBuilder()
      .overrides(bind[ConnectorMetrics].to[NoopConnectorMetrics])
      .overrides(bind[ThirdPartyDeveloperConnector].toInstance(connectors.tpdConnector))
      .overrides(bind[ThirdPartyApplicationProductionConnector].toInstance(connectors.tpaProductionConnector))
      .overrides(bind[ThirdPartyApplicationSandboxConnector].toInstance(connectors.tpaSandboxConnector))
      .in(Mode.Test)
      .build()
  }

  def buildRequestForAuthCondition(noAuthRequest: Request[AnyContentAsEmpty.type], authCondition: AuthCondition): Request[AnyContentAsEmpty.type] = {
    CSRFTokenHelper.addCSRFToken(authCondition match {
      case NoAuth => noAuthRequest
      case LoggedInDeveloper => FakeRequest(noAuthRequest.method, noAuthRequest.path).withCookies(Cookie(
        "PLAY2AUTH_SESS_ID",
        cookieSigner.sign(connectors.sessionId) + connectors.sessionId,
        None,
        "path",
        None,
        false,
        false
      ))
    })
  }

  def callEndpoint(endpoint: Endpoint, authCondition: AuthCondition): EndpointResult = {
    try {
      val path = s"/developer${endpoint.path}"
      val request = buildRequestForAuthCondition(FakeRequest(endpoint.verb, path), authCondition)
      val result = route(app, request).get
      status(result) match {
        case status: Int if 200 to 299 contains status => Success()
        case status: Int if 300 to 399 contains status => Redirect(redirectLocation(result).get)
        case 400 => BadRequest()
        case status => Unexpected(status)
      }
    } catch {
      case e: Exception => Error(e)
    }
  }

  val rules = Set(
    EndpointAccessRules(Endpoint("GET", "/login"), NoAuthAllowed),
    EndpointAccessRules(Endpoint("GET", "/locked"), NoAuthAllowed),
    EndpointAccessRules(Endpoint("GET", "/forgot-password"), NoAuthAllowed),
    EndpointAccessRules(Endpoint("POST", "/support"), Rule(NoAuth, BadRequest())),
    EndpointAccessRules(Endpoint("POST", "/reset-password"), Rule(NoAuth, Redirect("/developer/reset-password/error"))) // ???
  )

  def testEndpointsForAuthCondition(authCondition: AuthCondition, defaultResult: EndpointResult, setup: Unit => Unit) = {
    setup()
    s"test endpoints using $authCondition" should {
      Source.fromFile("conf/app.routes").getLines().flatMap(parseEndpoint).toSet foreach { endpoint: Endpoint =>
//      List("GET /applications  uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.ManageApplications.manageApps").flatMap(parseEndpoint).toSet foreach { endpoint: Endpoint =>
        val expectedResult = rules.filter(_.endpoint == endpoint).flatMap(_.rules).filter(_.authCondition == authCondition) match {
          case rules if rules.size == 1 => rules.head.result
          case rules if rules.size > 1 => fail(s"Invalid rule configuration, ${rules.size} rules matched endpoint $endpoint for condition $authCondition")
          case _ => defaultResult
        }
        s"give $expectedResult using $authCondition to access ${endpoint.verb} ${endpoint.path}" in {
          val result = callEndpoint(endpoint, authCondition)
          result shouldBe expectedResult
        }
      }
    }
  }

//  testEndpointsForAuthCondition(NoAuth, Redirect("/developer/login"))
  testEndpointsForAuthCondition(LoggedInDeveloper, Success(), _ => {
    connectors.userIsTeamMember()
    connectors.userAuthenticatesSuccessfully()
  })
}
