/*
 * Copyright 2020 HM Revenue & Customs
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

import controllers.ApplicationSummary
import domain.models.apidefinitions.AccessType
import domain.models.applications.{Role, State, TermsOfUseStatus}
import domain.models.developers.LoggedInState
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import play.api.test.FakeRequest
import uk.gov.hmrc.time.DateTimeUtils
import utils.ViewHelpers.{elementExistsByText, elementIdentifiedByAttrContainsText}
import utils.WithCSRFAddToken
import views.helper.CommonViewSpec
import views.html.{AddApplicationSubordinateEmptyNestView, ManageApplicationsView}

class ViewAllApplicationsPageSpec extends CommonViewSpec with WithCSRFAddToken {
  def isGreenAddProductionApplicationButtonVisible(document: Document) : Boolean ={
    val href = controllers.routes.AddApplication.addApplicationPrincipal().url

    val greenButtons = document.select(s"a[href=$href][class=button]")

    !greenButtons.isEmpty
  }

  "view all applications page" should {

    def renderPage(appSummaries: Seq[ApplicationSummary]) = {
      val request = FakeRequest()
      val loggedIn = utils.DeveloperSession("developer@example.com", "firstName", "lastname", loggedInState = LoggedInState.LOGGED_IN)
      val manageApplicationsView = app.injector.instanceOf[ManageApplicationsView]

      manageApplicationsView.render(appSummaries, request, loggedIn, messagesProvider, appConfig, "nav-section")
    }

    "show the applications page if there is more than 0 sandbox applications" in {

      val appName = "App name 1"
      val appEnvironment = "Sandbox"
      val appUserRole = Role.ADMINISTRATOR
      val appCreatedOn = DateTimeUtils.now
      val appLastAccess = appCreatedOn

      val appSummaries = Seq(ApplicationSummary("1111", appName, appEnvironment, appUserRole,
        TermsOfUseStatus.NOT_APPLICABLE, State.TESTING, appLastAccess, false, appCreatedOn, AccessType.STANDARD))

      val document = Jsoup.parse(renderPage(appSummaries).body)

      elementExistsByText(document, "h1", "View all applications") shouldBe true
      elementIdentifiedByAttrContainsText(document, "a", "data-app-name", appName) shouldBe true
      elementIdentifiedByAttrContainsText(document, "td", "data-app-lastAccess", "No API called") shouldBe true
      elementIdentifiedByAttrContainsText(document, "td", "data-app-user-role", "Admin") shouldBe true

      elementExistsByText(document, "p", "After testing in the sandbox, you can apply for production credentials.") shouldBe true

      isGreenAddProductionApplicationButtonVisible(document) shouldBe true
    }

    "hide Get production credentials button if there is more than 0 production applications" in {

      val appName = "App name 1"
      val appEnvironment = "Production"
      val appUserRole = Role.ADMINISTRATOR
      val appCreatedOn = DateTimeUtils.now
      val appLastAccess = appCreatedOn

      val appSummaries = Seq(ApplicationSummary("1111", appName, appEnvironment, appUserRole,
        TermsOfUseStatus.NOT_APPLICABLE, State.TESTING, appLastAccess, false, appCreatedOn, AccessType.STANDARD))

      val document = Jsoup.parse(renderPage(appSummaries).body)

      elementExistsByText(document, "h1", "View all applications") shouldBe true
      elementIdentifiedByAttrContainsText(document, "a", "data-app-name", appName) shouldBe true
      elementIdentifiedByAttrContainsText(document, "td", "data-app-lastAccess", "No API called") shouldBe true
      elementIdentifiedByAttrContainsText(document, "td", "data-app-user-role", "Admin") shouldBe true

      isGreenAddProductionApplicationButtonVisible(document) shouldBe false
      elementExistsByText(document, "p", "After testing in the sandbox, you can apply for production credentials.") shouldBe false
    }

    "show using privileged application credentials text if user is a collaborator on at least one privileged application" in {

      val appName = "App name 1"
      val appEnvironment = "Production"
      val appUserRole = Role.ADMINISTRATOR
      val appCreatedOn = DateTimeUtils.now
      val appLastAccess = appCreatedOn

      val appSummaries = Seq(ApplicationSummary("1111", appName, appEnvironment, appUserRole,
        TermsOfUseStatus.NOT_APPLICABLE, State.PRODUCTION, appLastAccess, false, appCreatedOn, AccessType.PRIVILEGED))

      val document = Jsoup.parse(renderPage(appSummaries).body)

      elementExistsByText(document, "h1", "View all applications") shouldBe true
      elementIdentifiedByAttrContainsText(document, "a", "data-app-name", appName) shouldBe true
      elementIdentifiedByAttrContainsText(document, "td", "data-app-lastAccess", "No API called") shouldBe true
      elementIdentifiedByAttrContainsText(document, "td", "data-app-user-role", "Admin") shouldBe true

      isGreenAddProductionApplicationButtonVisible(document) shouldBe false
      elementExistsByText(document, "h2", "Using privileged application credentials") shouldBe true
    }

    "hide using privileged application credentials text if user is not a collaborator on at least one privileged application" in {

      val appName = "App name 1"
      val appEnvironment = "Production"
      val appUserRole = Role.ADMINISTRATOR
      val appCreatedOn = DateTimeUtils.now
      val appLastAccess = appCreatedOn

      val appSummaries = Seq(ApplicationSummary("1111", appName, appEnvironment, appUserRole,
        TermsOfUseStatus.NOT_APPLICABLE, State.TESTING, appLastAccess, false, appCreatedOn, AccessType.STANDARD))

      val document = Jsoup.parse(renderPage(appSummaries).body)

      elementExistsByText(document, "h1", "View all applications") shouldBe true
      elementIdentifiedByAttrContainsText(document, "a", "data-app-name", appName) shouldBe true
      elementIdentifiedByAttrContainsText(document, "td", "data-app-lastAccess", "No API called") shouldBe true
      elementIdentifiedByAttrContainsText(document, "td", "data-app-user-role", "Admin") shouldBe true

      isGreenAddProductionApplicationButtonVisible(document) shouldBe false
      elementExistsByText(document, "h2", "Using privileged application credentials") shouldBe false
    }
  }

  "welcome to your account page" should {

    def renderPage(appSummaries: Seq[ApplicationSummary]) = {
      val request = FakeRequest().withCSRFToken
      val loggedIn = utils.DeveloperSession("developer@example.com", "firstName", "lastname", loggedInState = LoggedInState.LOGGED_IN)
      val addApplicationSubordinateEmptyNestView = app.injector.instanceOf[AddApplicationSubordinateEmptyNestView]

      addApplicationSubordinateEmptyNestView.render(request, loggedIn, messagesProvider, appConfig, "nav-section")
    }

    "show the empty nest page when there are no applications" in {

      val appSummaries = Seq()

      val document = Jsoup.parse(renderPage(appSummaries).body)

      elementExistsByText(document, "h1", "Add an application to the sandbox") shouldBe true
      elementExistsByText(document, "p", "To start using our RESTful APIs with your application, you'll need to:") shouldBe true
      elementExistsByText(document, "li", "add it to our test environment (sandbox)") shouldBe true
      elementExistsByText(document, "li", "choose which sandbox APIs you want to use") shouldBe true
      elementExistsByText(document, "li", "get credentials your application needs to interact with our APIs") shouldBe true
      elementExistsByText(document, "li", "test how your application integrates with our APIs") shouldBe true
      elementExistsByText(document, "strong", "Make sure your application complies with our terms of use") shouldBe true
      elementExistsByText(document, "li", "find out which RESTful and XML APIs are offered by HMRC") shouldBe true
      elementExistsByText(document, "li", "read up on authorisation") shouldBe true
      elementExistsByText(document, "li", "familiarise yourself with the OAuth 2.0 specification (opens in a new tab)") shouldBe true
    }
  }
}
