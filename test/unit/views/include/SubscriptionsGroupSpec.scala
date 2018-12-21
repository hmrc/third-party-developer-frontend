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

package unit.views.include

import config.ApplicationConfig
import controllers.APISubscriptions
import domain._
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.OneServerPerSuite
import play.api.i18n.Messages.Implicits._
import play.api.test.FakeRequest
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.time.DateTimeUtils
import utils.CSRFTokenHelper._

class SubscriptionsGroupSpec extends UnitSpec with MockitoSugar with OneServerPerSuite {
  val request = FakeRequest().withCSRFToken
  val appConfig = mock[ApplicationConfig]
  val loggedInUser = Developer("givenname.familyname@example.com", "Givenname", "Familyname")
  val applicationId = "1234"
  val clientId = "clientId123"
  val applicationName = "Test Application"
  val apiName = "Test API"
  val apiContext = "test"
  val apiVersion = "1.0"
  val subscriptionStatus = APISubscriptionStatus(apiName, apiName, apiContext, APIVersion(apiVersion, APIStatus.STABLE, None), false, false)
  val apiSubscriptions = Seq(APISubscriptions(apiName, apiName, apiContext, Seq(subscriptionStatus)))

  case class Page(role: Role, environment: Environment, state: ApplicationState) {
    lazy val body: Document = {
      val application = Application(applicationId, clientId, applicationName, DateTimeUtils.now, environment, Some("Description 1"),
        Set(Collaborator(loggedInUser.email, role)), state = state,
        access = Standard(redirectUris = Seq("https://red1.example.com", "https://red2.example.con"), termsAndConditionsUrl = Some("http://tnc-url.example.com")))

      Jsoup.parse(views.html.include.subscriptionsGroup.render(role, application, apiSubscriptions, hasAnySubscriptions = true, group = "Example", afterSubscriptionRedirectTo = SubscriptionRedirect.MANAGE_PAGE, applicationMessages, appConfig, request).body)
    }

    lazy val toggle = body.getElementById("test-1_0-toggle")
    lazy val requestChangeLink = Option(body.getElementsByClass("accordion__body__row__right--link").first)
  }

  "subscriptionsGroup" when {
    "logged in as a developer" should {
      val role = Role.DEVELOPER

      "render enabled toggles for a sandbox app" in {
        val page = Page(role, Environment.SANDBOX, ApplicationState.production(loggedInUser.email, ""))

        page.toggle.hasAttr("disabled") shouldBe false
        page.requestChangeLink shouldBe None
      }

      "render enabled toggles for a created production app" in {
        val page = Page(role, Environment.PRODUCTION, ApplicationState.testing)

        page.toggle.hasAttr("disabled") shouldBe false
        page.requestChangeLink shouldBe None
      }

      "render disabled toggles for a pending-gatekeeper-approval production app with no link to request change" in {
        val page = Page(role, Environment.PRODUCTION, ApplicationState.pendingGatekeeperApproval(loggedInUser.email))

        page.toggle.hasAttr("disabled") shouldBe true
        page.requestChangeLink shouldBe None
      }

      "render disabled toggles for a pending-requester-verification production app with no link to request change" in {
        val page = Page(role, Environment.PRODUCTION, ApplicationState.pendingRequesterVerification(loggedInUser.email, ""))

        page.toggle.hasAttr("disabled") shouldBe true
        page.requestChangeLink shouldBe None
      }

      "render disabled toggles for a checked production app with no link to request change" in {
        val page = Page(role, Environment.PRODUCTION, ApplicationState.production(loggedInUser.email, ""))

        page.toggle.hasAttr("disabled") shouldBe true
        page.requestChangeLink shouldBe None
      }
    }

    "logged in as an administrator" should {
      val role = Role.ADMINISTRATOR

      "render enabled toggles for a sandbox app" in {
        val page = Page(role, Environment.SANDBOX, ApplicationState.production(loggedInUser.email, ""))

        page.toggle.hasAttr("disabled") shouldBe false
        page.requestChangeLink shouldBe None
      }

      "render enabled toggles for a created production app" in {
        val page = Page(role, Environment.PRODUCTION, ApplicationState.testing)

        page.toggle.hasAttr("disabled") shouldBe false
        page.requestChangeLink shouldBe None
      }

      "render disabled toggles for a pending-gatekeeper-approval production app with a link to request change" in {
        val page = Page(role, Environment.PRODUCTION, ApplicationState.pendingGatekeeperApproval(loggedInUser.email))

        page.toggle.hasAttr("disabled") shouldBe true
        page.requestChangeLink shouldBe 'defined
      }

      "render disabled toggles for a pending-requester-verification production app with a link to request change" in {
        val page = Page(role, Environment.PRODUCTION, ApplicationState.pendingRequesterVerification(loggedInUser.email, ""))

        page.toggle.hasAttr("disabled") shouldBe true
        page.requestChangeLink shouldBe 'defined
      }

      "render disabled toggles for a checked production app with a link to request change" in {
        val page = Page(role, Environment.PRODUCTION, ApplicationState.production(loggedInUser.email, ""))

        page.toggle.hasAttr("disabled") shouldBe true
        page.requestChangeLink shouldBe 'defined
      }
    }
  }
}
