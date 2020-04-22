package config

import controllers.{routes, DevHubAuthWrapper}
import domain.LoggedInState
import org.scalatest.mockito.MockitoSugar
import org.scalatest.Matchers
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import play.api.libs.crypto.CookieSigner
import play.api.mvc.Results.EmptyContent
import play.api.test.FakeRequest
import service.SessionService
import uk.gov.hmrc.play.test.UnitSpec
import org.mockito.BDDMockito.given
import uk.gov.hmrc.http.HeaderCarrier
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import utils.DeveloperSession
import play.api.mvc.Results._
import play.api.http.Status._
import play.api.test.Helpers.{defaultAwaitTimeout, redirectLocation}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

//// TODO: Migrate some of these tests to DevHubAuthWrapper
class DevHubAuthWrapperSpec extends UnitSpec with MockitoSugar with Matchers with GuiceOneAppPerTest {
  "DebHubAuthWrapper" when {
    implicit val applicationConfig: ApplicationConfig = mock[ApplicationConfig]
    given(applicationConfig.securedCookie).willReturn(false)

    class TestDevHubAuthWrapper(implicit val appConfig: ApplicationConfig) extends DevHubAuthWrapper {
      override val sessionService: SessionService = mock[SessionService]
      override val cookieSigner: CookieSigner = fakeApplication.injector.instanceOf[CookieSigner]
    }

    val underTest = new TestDevHubAuthWrapper()
    val sessionId = "sessionId"

    "the user is logged in and" when {
      val developerSession = DeveloperSession("Email", "firstName", "lastName", loggedInState = LoggedInState.LOGGED_IN)

      implicit val request = FakeRequest().withCookies(underTest.createCookie(sessionId))
      given(underTest.sessionService.fetch(eqTo(sessionId))(any[HeaderCarrier])).willReturn(Some(developerSession.session))

      "controller action is decorated with loggedInAction" should {
        "successfully execute action" in {
          val action = underTest.loggedInAction { implicit request =>
            Future.successful(Ok(EmptyContent()))
          }

          val result = await(action()(request))
          status(result) shouldBe OK
        }
      }

      "controller action is decorated with atLeastPartLoggedInEnablingMfaAction" should {
        "successfully execute action" in {
          val action = underTest.atLeastPartLoggedInEnablingMfaAction { implicit request =>
            Future.successful(Ok(EmptyContent()))
          }

          val result = await(action()(request))
          status(result) shouldBe OK
        }
      }
    }

    "the user is part logged in and" when {
      val developerSession = utils.DeveloperSession("Email", "firstName", "lastName", loggedInState = LoggedInState.PART_LOGGED_IN_ENABLING_MFA)

      implicit val request = FakeRequest().withCookies(underTest.createCookie(sessionId))
      given(underTest.sessionService.fetch(eqTo(sessionId))(any[HeaderCarrier])).willReturn(Some(developerSession.session))

      "controller action is decorated with loggedInAction" should {
        "redirect to login page" in {
          val action = underTest.loggedInAction { implicit request =>
            Future.successful(Ok(EmptyContent()))
          }

          val result = await(action()(request))
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.UserLoginAccount.login().url)
        }
      }

      "controller action is decorated with atLeastPartLoggedInEnablingMfaAction" should {
        "successfully execute action" in {
          val action = underTest.atLeastPartLoggedInEnablingMfaAction { implicit request =>
            Future.successful(Ok(EmptyContent()))
          }

          val result = await(action()(request))
          status(result) shouldBe OK
        }
      }
    }
  }
}
