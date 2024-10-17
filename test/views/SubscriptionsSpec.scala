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

import scala.jdk.CollectionConverters._

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import views.helper.CommonViewSpec
import views.html.ManageSubscriptionsView

import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.twirl.api.Html

import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models.{Access, AccessType}
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.{ApplicationState, ApplicationWithCollaborators, ApplicationWithCollaboratorsFixtures, Collaborator, State}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{ApplicationId, ClientId, Environment}
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.apiplatform.modules.submissions.controllers.SubmissionActionBuilders.ApplicationStateFilter.pendingApprovalOrProduction
import uk.gov.hmrc.apiplatform.modules.tpd.test.builders.UserBuilder
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.DeveloperSessionBuilder
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.{EditApplicationForm, GroupedSubscriptions, PageData}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.ApplicationViewModel
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils._

class SubscriptionsSpec extends CommonViewSpec
    with WithCSRFAddToken
    with LocalUserIdTracker
    with DeveloperSessionBuilder
    with UserBuilder
    with FixedClock
    with ApplicationWithCollaboratorsFixtures {

  val manageSubscriptions = app.injector.instanceOf[ManageSubscriptionsView]

  trait Setup {

    def elementExistsByText(doc: Document, elementType: String, elementText: String): Boolean = {
      doc.select(elementType).asScala.exists(node => node.text.trim == elementText)
    }

    def elementExistsById(doc: Document, id: String): Boolean = doc.select(s"#$id").asScala.nonEmpty
  }

  "Subscriptions page" should {
    val developer = buildUser("Test".toLaxEmail, "Test", "Test").loggedIn
    val baseState = ApplicationState(State.PRODUCTION, Some("somebody@example.com"), Some("somebody"), Some(""), instant)

    val productionApplicationPendingGatekeeperApproval    = standardApp.withState(appStatePendingGatekeeperApproval)
    val productionApplicationPendingRequesterVerification = standardApp.withState(appStatePendingRequesterVerification)
    val productionApplication                             = standardApp.withState(appStateProduction)
    val productionApplicationTesting                      = standardApp.withState(appStateTesting)

    val sandboxApplicationTesting = standardApp.withEnvironment(Environment.SANDBOX).withState(appStateTesting)

    def renderPageForApplicationAndRole(application: ApplicationWithCollaborators, role: Collaborator.Role, pageData: PageData, request: FakeRequest[AnyContentAsEmpty.type]) = {
      manageSubscriptions.render(
        role,
        pageData,
        EditApplicationForm.withData(productionApplicationTesting),
        ApplicationViewModel(application, false, false),
        Some(GroupedSubscriptions(Seq.empty, Seq.empty)),
        List.empty,
        ApplicationId.random,
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

      val pageData: PageData = PageData(productionApplication, None, List.empty)

      val page: Html = renderPageForApplicationAndRole(productionApplication, Collaborator.Roles.ADMINISTRATOR, pageData, request)

      page.contentType should include("text/html")
      page.body should include("For security reasons we must review any API subscription changes.")

      val document: Document = Jsoup.parse(page.body)

      elementExistsByText(document, "h1", "Manage API subscriptions") shouldBe true
    }

    "render for a Developer with a PRODUCTION app in ACTIVE state" in new Setup {
      val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withCSRFToken

      val pageData: PageData = PageData(productionApplication, None, List.empty)

      val page: Html = renderPageForApplicationAndRole(productionApplication, Collaborator.Roles.DEVELOPER, pageData, request)

      page.contentType should include("text/html")
      page.body should include("You need admin rights to make API subscription changes.")

      val document: Document = Jsoup.parse(page.body)

      elementExistsByText(document, "h1", "Manage API subscriptions") shouldBe true
    }

    "render for an Admin with a PRODUCTION app in CREATED state" in new Setup {
      val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withCSRFToken

      val pageData: PageData = PageData(productionApplicationTesting, None, List.empty)

      val page: Html = renderPageForApplicationAndRole(productionApplicationTesting, Collaborator.Roles.ADMINISTRATOR, pageData, request)

      page.contentType should include("text/html")
      page.body shouldNot include("For security reasons we must review any API subscription changes.")

      val document: Document = Jsoup.parse(page.body)

      elementExistsByText(document, "h1", "Manage API subscriptions") shouldBe true
    }

    "render for a Develolper with a PRODUCTION app in CREATED state" in new Setup {
      val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withCSRFToken

      val pageData: PageData = PageData(productionApplicationTesting, None, List.empty)

      val page: Html = renderPageForApplicationAndRole(productionApplicationTesting, Collaborator.Roles.DEVELOPER, pageData, request)

      page.contentType should include("text/html")
      page.body shouldNot include("For security reasons we must review any API subscription changes.")

      val document: Document = Jsoup.parse(page.body)

      elementExistsByText(document, "h1", "Manage API subscriptions") shouldBe true
    }

    "render for an Admin with a PRODUCTION app in PENDING_GATEKEEPER_APPROVAL state" in new Setup {
      val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withCSRFToken

      val pageData: PageData = PageData(productionApplicationPendingGatekeeperApproval, None, List.empty)

      val page: Html = renderPageForApplicationAndRole(productionApplicationPendingGatekeeperApproval, Collaborator.Roles.ADMINISTRATOR, pageData, request)

      page.contentType should include("text/html")
      page.body should include("For security reasons we must review any API subscription changes.")

      val document: Document = Jsoup.parse(page.body)

      elementExistsByText(document, "h1", "Manage API subscriptions") shouldBe true
    }

    "render for an Admin with a PRODUCTION app in PENDING_REQUESTER_APPROVAL state" in new Setup {
      val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withCSRFToken

      val pageData: PageData = PageData(productionApplicationPendingRequesterVerification, None, List.empty)

      val page: Html = renderPageForApplicationAndRole(productionApplicationPendingRequesterVerification, Collaborator.Roles.ADMINISTRATOR, pageData, request)

      page.contentType should include("text/html")
      page.body should include("For security reasons we must review any API subscription changes.")

      val document: Document = Jsoup.parse(page.body)

      elementExistsByText(document, "h1", "Manage API subscriptions") shouldBe true
    }

    "render for an Admin with a SANDBOX app in CREATED state" in new Setup {
      val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withCSRFToken

      val pageData: PageData = PageData(sandboxApplicationTesting, None, List.empty)

      val page: Html = renderPageForApplicationAndRole(sandboxApplicationTesting, Collaborator.Roles.ADMINISTRATOR, pageData, request)

      page.contentType should include("text/html")
      page.body shouldNot include("For security reasons we must review any API subscription changes.")

      val document: Document = Jsoup.parse(page.body)

      elementExistsByText(document, "h1", "Manage API subscriptions") shouldBe true
    }

    "render for a Developer with a SANDBOX app in CREATED state" in new Setup {
      val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withCSRFToken

      val pageData: PageData = PageData(sandboxApplicationTesting, None, List.empty)

      val page: Html = renderPageForApplicationAndRole(sandboxApplicationTesting, Collaborator.Roles.DEVELOPER, pageData, request)

      page.contentType should include("text/html")
      page.body shouldNot include("For security reasons we must review any API subscription changes.")

      val document: Document = Jsoup.parse(page.body)

      elementExistsByText(document, "h1", "Manage API subscriptions") shouldBe true
    }
  }
}
