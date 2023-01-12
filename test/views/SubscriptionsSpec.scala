/*
 * Copyright 2023 HM Revenue & Customs
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

package views

import java.time.LocalDateTime
import scala.collection.JavaConverters._

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import views.helper.CommonViewSpec
import views.html.ManageSubscriptionsView

import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.twirl.api.Html

import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.{DeveloperBuilder, DeveloperSessionBuilder}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.{EditApplicationForm, GroupedSubscriptions, PageData}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.ApplicationViewModel
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.LoggedInState
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils._

class SubscriptionsSpec extends CommonViewSpec
    with WithCSRFAddToken
    with LocalUserIdTracker
    with DeveloperSessionBuilder
    with DeveloperBuilder {

  val manageSubscriptions = app.injector.instanceOf[ManageSubscriptionsView]

  trait Setup {

    def elementExistsByText(doc: Document, elementType: String, elementText: String): Boolean = {
      doc.select(elementType).asScala.exists(node => node.text.trim == elementText)
    }

    def elementExistsById(doc: Document, id: String): Boolean = doc.select(s"#$id").asScala.nonEmpty
  }

  def buildApplication(applicationState: ApplicationState, environment: Environment): Application = Application(
    ApplicationId("Test Application ID"),
    ClientId("Test Application Client ID"),
    "Test Application",
    LocalDateTime.now(),
    Some(LocalDateTime.now()),
    None,
    grantLength,
    environment,
    Some("Test Application"),
    Set.empty,
    Standard(),
    applicationState,
    None
  )

  "Subscriptions page" should {
    val developer = buildDeveloperSession(loggedInState = LoggedInState.LOGGED_IN, buildDeveloperWithRandomId("Test", "Test", "Test", None))

    val productionApplicationPendingGatekeeperApproval    = buildApplication(ApplicationState.pendingGatekeeperApproval("somebody@example.com", "somebody"), Environment.PRODUCTION)
    val productionApplicationPendingRequesterVerification =
      buildApplication(ApplicationState.pendingRequesterVerification("somebody@example.com", "somebody", ""), Environment.PRODUCTION)
    val productionApplication                             = buildApplication(ApplicationState.production("somebody@example.com", "somebody", ""), Environment.PRODUCTION)
    val productionApplicationTesting                      = buildApplication(ApplicationState.testing, Environment.PRODUCTION)

    val sandboxApplicationTesting = buildApplication(ApplicationState.testing, Environment.SANDBOX)

    def renderPageForApplicationAndRole(application: Application, role: CollaboratorRole, pageData: PageData, request: FakeRequest[AnyContentAsEmpty.type]) = {
      manageSubscriptions.render(
        role,
        pageData,
        EditApplicationForm.withData(productionApplicationTesting),
        ApplicationViewModel(application, false, false),
        Some(GroupedSubscriptions(Seq.empty, Seq.empty)),
        Map.empty,
        ApplicationId(""),
        Some(createFraudPreventionNavLinkViewModel(isVisible = true, "some/url")),
        request,
        developer,
        messagesProvider,
        appConfig,
        "subscriptions"
      )
    }

    "render for an Admin with a PRODUCTION app in ACTIVE state" in new Setup {
      val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withCSRFToken

      val pageData: PageData = PageData(productionApplication, None, Map.empty)

      val page: Html = renderPageForApplicationAndRole(productionApplication, CollaboratorRole.ADMINISTRATOR, pageData, request)

      page.contentType should include("text/html")
      page.body should include("For security reasons we must review any API subscription changes.")

      val document: Document = Jsoup.parse(page.body)

      elementExistsByText(document, "h1", "Manage API subscriptions") shouldBe true
    }

    "render for a Developer with a PRODUCTION app in ACTIVE state" in new Setup {
      val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withCSRFToken

      val pageData: PageData = PageData(productionApplication, None, Map.empty)

      val page: Html = renderPageForApplicationAndRole(productionApplication, CollaboratorRole.DEVELOPER, pageData, request)

      page.contentType should include("text/html")
      page.body should include("You need admin rights to make API subscription changes.")

      val document: Document = Jsoup.parse(page.body)

      elementExistsByText(document, "h1", "Manage API subscriptions") shouldBe true
    }

    "render for an Admin with a PRODUCTION app in CREATED state" in new Setup {
      val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withCSRFToken

      val pageData: PageData = PageData(productionApplicationTesting, None, Map.empty)

      val page: Html = renderPageForApplicationAndRole(productionApplicationTesting, CollaboratorRole.ADMINISTRATOR, pageData, request)

      page.contentType should include("text/html")
      page.body shouldNot include("For security reasons we must review any API subscription changes.")

      val document: Document = Jsoup.parse(page.body)

      elementExistsByText(document, "h1", "Manage API subscriptions") shouldBe true
    }

    "render for a Develolper with a PRODUCTION app in CREATED state" in new Setup {
      val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withCSRFToken

      val pageData: PageData = PageData(productionApplicationTesting, None, Map.empty)

      val page: Html = renderPageForApplicationAndRole(productionApplicationTesting, CollaboratorRole.DEVELOPER, pageData, request)

      page.contentType should include("text/html")
      page.body shouldNot include("For security reasons we must review any API subscription changes.")

      val document: Document = Jsoup.parse(page.body)

      elementExistsByText(document, "h1", "Manage API subscriptions") shouldBe true
    }

    "render for an Admin with a PRODUCTION app in PENDING_GATEKEEPER_APPROVAL state" in new Setup {
      val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withCSRFToken

      val pageData: PageData = PageData(productionApplicationPendingGatekeeperApproval, None, Map.empty)

      val page: Html = renderPageForApplicationAndRole(productionApplicationPendingGatekeeperApproval, CollaboratorRole.ADMINISTRATOR, pageData, request)

      page.contentType should include("text/html")
      page.body should include("For security reasons we must review any API subscription changes.")

      val document: Document = Jsoup.parse(page.body)

      elementExistsByText(document, "h1", "Manage API subscriptions") shouldBe true
    }

    "render for an Admin with a PRODUCTION app in PENDING_REQUESTER_APPROVAL state" in new Setup {
      val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withCSRFToken

      val pageData: PageData = PageData(productionApplicationPendingRequesterVerification, None, Map.empty)

      val page: Html = renderPageForApplicationAndRole(productionApplicationPendingRequesterVerification, CollaboratorRole.ADMINISTRATOR, pageData, request)

      page.contentType should include("text/html")
      page.body should include("For security reasons we must review any API subscription changes.")

      val document: Document = Jsoup.parse(page.body)

      elementExistsByText(document, "h1", "Manage API subscriptions") shouldBe true
    }

    "render for an Admin with a SANDBOX app in CREATED state" in new Setup {
      val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withCSRFToken

      val pageData: PageData = PageData(sandboxApplicationTesting, None, Map.empty)

      val page: Html = renderPageForApplicationAndRole(sandboxApplicationTesting, CollaboratorRole.ADMINISTRATOR, pageData, request)

      page.contentType should include("text/html")
      page.body shouldNot include("For security reasons we must review any API subscription changes.")

      val document: Document = Jsoup.parse(page.body)

      elementExistsByText(document, "h1", "Manage API subscriptions") shouldBe true
    }

    "render for a Developer with a SANDBOX app in CREATED state" in new Setup {
      val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withCSRFToken

      val pageData: PageData = PageData(sandboxApplicationTesting, None, Map.empty)

      val page: Html = renderPageForApplicationAndRole(sandboxApplicationTesting, CollaboratorRole.DEVELOPER, pageData, request)

      page.contentType should include("text/html")
      page.body shouldNot include("For security reasons we must review any API subscription changes.")

      val document: Document = Jsoup.parse(page.body)

      elementExistsByText(document, "h1", "Manage API subscriptions") shouldBe true
    }
  }
}
