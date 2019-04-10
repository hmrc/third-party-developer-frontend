/*
 * Copyright 2019 HM Revenue & Customs
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

package unit.controllers

import config.{ApplicationConfig, ErrorHandler}
import connectors.ThirdPartyDeveloperConnector
import controllers._
import domain._
import org.joda.time.DateTimeZone
import org.mockito.BDDMockito.given
import org.mockito.Matchers.{eq => mockEq, _}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import play.api.test.{FakeRequest, Helpers, Writeables}
import play.filters.csrf.CSRF.TokenProvider
import service.{ApplicationService, AuditService, SessionService}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import uk.gov.hmrc.time.DateTimeUtils
import utils.WithCSRFAddToken
import utils.WithLoggedInSession._

import scala.concurrent.Future

class ManageApplicationUpliftSpec extends UnitSpec with MockitoSugar with WithFakeApplication with ScalaFutures with Writeables with WithCSRFAddToken {
  implicit val materializer = fakeApplication.materializer
  val appId = "1234"
  val clientId = "clientId456"
  val appName = "app Name!"
  val loggedInUser = Developer("thirdpartydeveloper@example.com", "John", "Doe")

  val sessionId = "sessionId"
  val session = Session(sessionId, loggedInUser)

  val application = Application(appId, clientId, "App name 1", DateTimeUtils.now, Environment.PRODUCTION, Some("Description 1"),
    Set(Collaborator(loggedInUser.email, Role.ADMINISTRATOR)), state = ApplicationState.testing)
  val tokens = ApplicationTokens(EnvironmentToken("clientId", Seq(aClientSecret("secret")), "token"))

  Helpers.running(fakeApplication) {

    trait Setup {

      val underTest = new ManageApplications(
        mock[ApplicationService],
        mock[ThirdPartyDeveloperConnector],
        mock[SessionService],
        mock[AuditService],
        mock[ErrorHandler],
        mock[ApplicationConfig]
      )

      given(underTest.sessionService.fetch(mockEq(sessionId))(any[HeaderCarrier])).willReturn(Some(session))
      val sessionParams = Seq("csrfToken" -> fakeApplication.injector.instanceOf[TokenProvider].generateToken)

      def mockLogout() =
        given(underTest.sessionService.destroy(mockEq(session.sessionId))(any[HeaderCarrier]))
          .willReturn(Future.successful(204))

      val loggedOutRequest = FakeRequest().withSession(sessionParams: _*)
      val loggedInRequest = FakeRequest().withLoggedIn(underTest)(sessionId).withSession(sessionParams: _*)
    }


  }

  private def aClientSecret(secret: String) = ClientSecret(secret, secret, DateTimeUtils.now.withZone(DateTimeZone.getDefault))
}
