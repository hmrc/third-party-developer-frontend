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

package unit.views

import config.ApplicationConfig
import controllers.{EditApplicationForm, GroupedSubscriptions, PageData}
import domain._
import org.joda.time.DateTime
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.OneServerPerSuite
import play.api.i18n.Messages.Implicits._
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import uk.gov.hmrc.play.test.UnitSpec
import utils.CSRFTokenHelper._
import views.html.subscriptions

import scala.collection.JavaConversions._

class SubscriptionsSpec extends UnitSpec with OneServerPerSuite with MockitoSugar {

  val appConfig = mock[ApplicationConfig]

  trait Setup {

    def elementExistsByText(doc: Document, elementType: String, elementText: String): Boolean = {
      doc.select(elementType).exists(node => node.text.trim == elementText)
    }

    def elementExistsById(doc: Document, id: String): Boolean = doc.select(s"#$id").nonEmpty
  }

  def buildApplication(applicationState: ApplicationState, environment: Environment) = Application(
    "Test Application ID",
    "Test Application Client ID",
    "Test Application",
    DateTime.now(),
    environment,
    Some("Test Application"),
    Set.empty,
    Standard(),
    false,
    applicationState,
    None
  )

  "Subscriptions page" should {
    val developer = Developer("Test", "Test", "Test", None, loggedInState = LoggedInState.LOGGED_IN)

    val productionApplicationPendingGatekeeperApproval = buildApplication(ApplicationState.pendingGatekeeperApproval("somebody@example.com"), Environment.PRODUCTION)
    val productionApplicationPendingRequesterVerification = buildApplication(ApplicationState.pendingRequesterVerification("somebody@example.com", ""), Environment.PRODUCTION)
    val productionApplication = buildApplication(ApplicationState.production("somebody@example.com", ""), Environment.PRODUCTION)
    val productionApplicationTesting = buildApplication(ApplicationState.testing, Environment.PRODUCTION)

    val sandboxApplicationTesting = buildApplication(ApplicationState.testing, Environment.SANDBOX)

    def renderPageForApplicationAndRole(application: Application, role: Role, pageData: PageData, request: FakeRequest[AnyContentAsEmpty.type]) = {
      subscriptions.render(
        role,
        pageData,
        EditApplicationForm.withData(productionApplicationTesting),
        application,
        Some(GroupedSubscriptions(Seq.empty, Seq.empty)),
        "",
        false,
        request,
        developer,
        applicationMessages,
        appConfig,
        "subscriptions"
      )
    }

    "render for an Admin with a PRODUCTION app in ACTIVE state" in new Setup {
      val request = FakeRequest().withCSRFToken

      val pageData = PageData(productionApplication, null, None)

      val page = renderPageForApplicationAndRole(productionApplication, Role.ADMINISTRATOR, pageData, request)

      page.contentType should include("text/html")
      page.body should include("For security reasons we must review any API subscription changes.")

      val document = Jsoup.parse(page.body)

      elementExistsByText(document, "h1", "Manage API subscriptions") shouldBe true
    }

    "render for a Developer with a PRODUCTION app in ACTIVE state" in new Setup {
      val request = FakeRequest().withCSRFToken

      val pageData = PageData(productionApplication, null, None)

      val page = renderPageForApplicationAndRole(productionApplication, Role.DEVELOPER, pageData, request)

      page.contentType should include("text/html")
      page.body should include("You need admin rights to make API subscription changes.")

      val document = Jsoup.parse(page.body)

      elementExistsByText(document, "h1", "Manage API subscriptions") shouldBe true
    }

    "render for an Admin with a PRODUCTION app in CREATED state" in new Setup {
      val request = FakeRequest().withCSRFToken

      val pageData = PageData(productionApplicationTesting, null, None)

      val page = renderPageForApplicationAndRole(productionApplicationTesting, Role.ADMINISTRATOR, pageData, request)

      page.contentType should include("text/html")
      page.body shouldNot include("For security reasons we must review any API subscription changes.")

      val document = Jsoup.parse(page.body)

      elementExistsByText(document, "h1", "Manage API subscriptions") shouldBe true
    }

    "render for a Develolper with a PRODUCTION app in CREATED state" in new Setup {
      val request = FakeRequest().withCSRFToken

      val pageData = PageData(productionApplicationTesting, null, None)

      val page = renderPageForApplicationAndRole(productionApplicationTesting, Role.DEVELOPER, pageData, request)

      page.contentType should include("text/html")
      page.body shouldNot include("For security reasons we must review any API subscription changes.")

      val document = Jsoup.parse(page.body)

      elementExistsByText(document, "h1", "Manage API subscriptions") shouldBe true
    }

    "render for an Admin with a PRODUCTION app in PENDING_GATEKEEPER_APPROVAL state" in new Setup {
      val request = FakeRequest().withCSRFToken

      val pageData = PageData(productionApplicationPendingGatekeeperApproval, null, None)

      val page = renderPageForApplicationAndRole(productionApplicationPendingGatekeeperApproval, Role.ADMINISTRATOR, pageData, request)

      page.contentType should include("text/html")
      page.body should include("For security reasons we must review any API subscription changes.")

      val document = Jsoup.parse(page.body)

      elementExistsByText(document, "h1", "Manage API subscriptions") shouldBe true
    }

    "render for an Admin with a PRODUCTION app in PENDING_REQUESTER_APPROVAL state" in new Setup {
      val request = FakeRequest().withCSRFToken

      val pageData = PageData(productionApplicationPendingRequesterVerification, null, None)

      val page = renderPageForApplicationAndRole(productionApplicationPendingRequesterVerification, Role.ADMINISTRATOR, pageData, request)

      page.contentType should include("text/html")
      page.body should include("For security reasons we must review any API subscription changes.")

      val document = Jsoup.parse(page.body)

      elementExistsByText(document, "h1", "Manage API subscriptions") shouldBe true
    }

    "render for an Admin with a SANDBOX app in CREATED state" in new Setup {
      val request = FakeRequest().withCSRFToken

      val pageData = PageData(sandboxApplicationTesting, null, None)

      val page = renderPageForApplicationAndRole(sandboxApplicationTesting, Role.ADMINISTRATOR, pageData, request)

      page.contentType should include("text/html")
      page.body shouldNot include("For security reasons we must review any API subscription changes.")

      val document = Jsoup.parse(page.body)

      elementExistsByText(document, "h1", "Manage API subscriptions") shouldBe true
    }

    "render for a Developer with a SANDBOX app in CREATED state" in new Setup {
      val request = FakeRequest().withCSRFToken

      val pageData = PageData(sandboxApplicationTesting, null, None)

      val page = renderPageForApplicationAndRole(sandboxApplicationTesting, Role.DEVELOPER, pageData, request)

      page.contentType should include("text/html")
      page.body shouldNot include("For security reasons we must review any API subscription changes.")

      val document = Jsoup.parse(page.body)

      elementExistsByText(document, "h1", "Manage API subscriptions") shouldBe true
    }
  }
}
