/*
 * Copyright 2018 HM Revenue & Customs
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

import config.ApplicationConfig
import controllers.Navigation
import domain.{Developer, Session}
import org.mockito.BDDMockito._
import org.mockito.Matchers
import org.mockito.Matchers._
import org.scalatest.mockito.MockitoSugar
import play.api.test.FakeRequest
import service.SessionService
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import utils.WithLoggedInSession._

import scala.concurrent.Future._
import uk.gov.hmrc.http.HeaderCarrier

class NavigationSpec extends UnitSpec with MockitoSugar with WithFakeApplication {

  implicit val materializer = fakeApplication.materializer
  val loggedInUser = Developer("thirdpartydeveloper@example.com", "John", "Doe")
  val sessionId = "sessionId"
  val session = Session(sessionId, loggedInUser)
  var userPassword = "Password1!"

  trait Setup {
    val underTest = new Navigation {
      override val sessionService = mock[SessionService]
      override val appConfig = mock[ApplicationConfig]
    }

    def mockSuccessfulAuthentication(loggedInUser: Developer) =
      given(underTest.sessionService.fetch(Matchers.eq(sessionId))(any[HeaderCarrier])).willReturn(successful(Some(Session(sessionId, loggedInUser))))
  }

  "navigation" should {
    "return navigation links when user not logged in" in new Setup {
      val result = await(underTest.navLinks()(FakeRequest()))

      status(result) shouldBe 200

      bodyOf(result) shouldBe """[{"label":"Register","href":"/developer/registration","truncate":false},{"label":"Sign in","href":"/developer/login","truncate":false}]"""
    }

    "return navigation links when user is logged in" in new Setup {
      val request = FakeRequest().withLoggedIn(underTest)(sessionId)
      mockSuccessfulAuthentication(loggedInUser)

      val result = await(underTest.navLinks()(request))

      status(result) shouldBe 200

      bodyOf(result) shouldBe """[{"label":"John Doe","href":"/developer/profile","truncate":false},{"label":"Sign out","href":"/developer/logout/survey","truncate":false}]"""
    }
  }
}
