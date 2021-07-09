/*
 * Copyright 2021 HM Revenue & Customs
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

import domain.models.controllers.{ManageApplicationsViewModel, ProductionApplicationSummary, SandboxApplicationSummary, ApplicationSummary}
import domain.models.apidefinitions.AccessType
import domain.models.applications.{ApplicationId, CollaboratorRole, State, TermsOfUseStatus}
import domain.models.developers.LoggedInState
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import play.api.test.FakeRequest
import uk.gov.hmrc.time.DateTimeUtils
import utils.ViewHelpers.{elementExistsByText, elementIdentifiedByAttrContainsText}
import utils.WithCSRFAddToken
import views.helper.CommonViewSpec
import views.html.{AddApplicationSubordinateEmptyNestView, ManageApplicationsView}
import views.helper.EnvironmentNameService

class ViewAllApplicationsPageSpec extends CommonViewSpec with WithCSRFAddToken {
  def isGreenAddProductionApplicationButtonVisible(document: Document): Boolean = {
    val href = controllers.routes.AddApplication.addApplicationPrincipal().url

    val greenButtons = document.select(s"a[href=$href][class=govuk-button]")

    !greenButtons.isEmpty
  }

  val applicationId = ApplicationId("1111")

  trait EnvNames {
    def principalCapitalized = principal.capitalize
    def subordinateCapitalized = subordinate.capitalize
    def principal: String
    def subordinate: String
    def subordinateWording: String
  }
  
  trait ProdAndET extends EnvNames {
    val principal = "Production"
    val subordinate = "Sandbox"
    val subordinateWording = "the sandbox"
  }

  trait QaAndDev extends EnvNames {
    val principal = "QA"
    val subordinate = "Development"
    val subordinateWording = "development"
  }
  
  trait Setup {
    self: EnvNames =>

    def showsHeading()(implicit document: Document) = 
      elementExistsByText(document, "h1", "View all applications") shouldBe true

    def showsAppName(appName: String)(implicit document: Document) =
      elementIdentifiedByAttrContainsText(document, "a", "data-app-name", appName) shouldBe true
      
    def showsSubordinateAppsHeading()(implicit document: Document) =
      elementExistsByText(document, "th", s"$subordinateCapitalized applications") shouldBe true

    def showsPrincipalAppsHeading()(implicit document: Document) =
      elementExistsByText(document, "th", s"$principalCapitalized applications") shouldBe true

    def hidesSubordinateAppsHeading()(implicit document: Document) =
      elementExistsByText(document, "th", s"$subordinateCapitalized applications") shouldBe false

    def hidesPrincipalAppsHeading()(implicit document: Document) =
      elementExistsByText(document, "th", s"$principalCapitalized applications") shouldBe false
    
    def showsGetCredentialsButton()(implicit document: Document) = 
      isGreenAddProductionApplicationButtonVisible(document) shouldBe true

    def doesNotShowsGetCredentialsButton()(implicit document: Document) =
      isGreenAddProductionApplicationButtonVisible(document) shouldBe false

    def showsAfterTestingMessage()(implicit document: Document) =
      elementExistsByText(document, "p", s"After testing in ${subordinateWording}, you can apply for ${principal.toLowerCase} credentials.") shouldBe true

    def showsPriviledAppsMessage()(implicit document: Document) =
      elementExistsByText(document, "h2", "Using privileged application credentials") shouldBe true

    def hidesPriviledAppsMessage()(implicit document: Document) =
      elementExistsByText(document, "h2", "Using privileged application credentials") shouldBe false

    when(appConfig.nameOfPrincipalEnvironment).thenReturn(principal)
    when(appConfig.nameOfSubordinateEnvironment).thenReturn(subordinate)
  }


  val environmentNameService = new EnvironmentNameService(appConfig)

  "view all applications page" should {

    def renderPage(sandboxAppSummaries: Seq[SandboxApplicationSummary], productionAppSummaries: Seq[ProductionApplicationSummary], upliftableApplicationIds: Set[ApplicationId]) = {
      val request = FakeRequest()
      val loggedIn = utils.DeveloperSession("developer@example.com", "firstName", "lastname", loggedInState = LoggedInState.LOGGED_IN)
      val manageApplicationsView = app.injector.instanceOf[ManageApplicationsView]

      manageApplicationsView.render(ManageApplicationsViewModel(sandboxAppSummaries, productionAppSummaries, upliftableApplicationIds), request, loggedIn, messagesProvider, appConfig, "nav-section", environmentNameService)
    }

    "show the applications page if there is more than 0 sandbox applications and environment is Prod/Sandbox" in new ProdAndET with Setup {

      val appName = "App name 1"
      val appUserRole = CollaboratorRole.ADMINISTRATOR
      val appCreatedOn = DateTimeUtils.now
      val appLastAccess = appCreatedOn

      val sandboxAppSummaries = Seq(
        SandboxApplicationSummary(
          applicationId,
          appName,
          appUserRole,
          TermsOfUseStatus.NOT_APPLICABLE,
          State.TESTING,
          appLastAccess,
          false,
          appCreatedOn,
          AccessType.STANDARD
        )
      )

      implicit val document = Jsoup.parse(renderPage(sandboxAppSummaries, Seq.empty, Set(applicationId)).body)
      
      showsHeading()
      showsAppName(appName)
      showsSubordinateAppsHeading()
      hidesPrincipalAppsHeading()

      elementIdentifiedByAttrContainsText(document, "td", "data-app-lastAccess", "No API called") shouldBe true
      elementIdentifiedByAttrContainsText(document, "td", "data-app-user-role", "Admin") shouldBe true

      showsAfterTestingMessage()

      showsGetCredentialsButton()
    }

    "show the applications page if there is more than 0 sandbox applications and environment is QA/Dev" in new QaAndDev with Setup {
      val appName = "App name 1"
      val appUserRole = CollaboratorRole.ADMINISTRATOR
      val appCreatedOn = DateTimeUtils.now
      val appLastAccess = appCreatedOn

      val sandboxAppSummaries = Seq(
          SandboxApplicationSummary(
            ApplicationId("1111"), 
            appName, 
            appUserRole,
            TermsOfUseStatus.NOT_APPLICABLE,
            State.TESTING,
            appLastAccess,
            false,
            appCreatedOn,
            AccessType.STANDARD
          ))

      implicit val document = Jsoup.parse(renderPage(sandboxAppSummaries, Seq.empty, Set(applicationId)).body)

      showsHeading()
      showsAppName(appName)
      showsSubordinateAppsHeading()
      hidesPrincipalAppsHeading()
      elementIdentifiedByAttrContainsText(document, "td", "data-app-lastAccess", "No API called") shouldBe true
      elementIdentifiedByAttrContainsText(document, "td", "data-app-user-role", "Admin") shouldBe true

      showsAfterTestingMessage()

      showsGetCredentialsButton()
    }

    "show using privileged application credentials text if user is a collaborator on at least one privileged application" in new ProdAndET with Setup {

      val appName = "App name 1"
      val appUserRole = CollaboratorRole.ADMINISTRATOR
      val appCreatedOn = DateTimeUtils.now
      val appLastAccess = appCreatedOn

      val productionAppSummaries = Seq(
        ProductionApplicationSummary(
          applicationId,
          appName,
          appUserRole,
          TermsOfUseStatus.NOT_APPLICABLE,
          State.PRODUCTION,
          appLastAccess,
          false,
          appCreatedOn,
          AccessType.PRIVILEGED
        )
      )

      implicit val document = Jsoup.parse(renderPage(Seq.empty, productionAppSummaries, Set(applicationId)).body)

      showsHeading()
      showsAppName(appName)
      elementIdentifiedByAttrContainsText(document, "td", "data-app-lastAccess", "No API called") shouldBe true
      elementIdentifiedByAttrContainsText(document, "td", "data-app-user-role", "Admin") shouldBe true

      doesNotShowsGetCredentialsButton()

      showsPriviledAppsMessage()
    }

    "hide using privileged application credentials text if user is not a collaborator on at least one privileged application" in new ProdAndET with Setup {

      val appName = "App name 1"
      val appUserRole = CollaboratorRole.ADMINISTRATOR
      val appCreatedOn = DateTimeUtils.now
      val appLastAccess = appCreatedOn

      val productionAppSummaries = Seq(
        ProductionApplicationSummary(
          applicationId,
          appName,
          appUserRole,
          TermsOfUseStatus.NOT_APPLICABLE,
          State.TESTING,
          appLastAccess,
          false,
          appCreatedOn,
          AccessType.STANDARD
        )
      )

      implicit val document = Jsoup.parse(renderPage(Seq.empty, productionAppSummaries, Set(applicationId)).body)

      showsHeading()
      showsAppName(appName)
      elementIdentifiedByAttrContainsText(document, "a", "data-app-name", appName) shouldBe true
      elementIdentifiedByAttrContainsText(document, "td", "data-app-lastAccess", "No API called") shouldBe true
      elementIdentifiedByAttrContainsText(document, "td", "data-app-user-role", "Admin") shouldBe true

      doesNotShowsGetCredentialsButton()
      elementExistsByText(document, "h2", "Using privileged application credentials") shouldBe false
    }
  }

  "welcome to your account page" should {

    def renderPage(appSummaries: Seq[ApplicationSummary]) = {
      val request = FakeRequest().withCSRFToken
      val loggedIn = utils.DeveloperSession("developer@example.com", "firstName", "lastname", loggedInState = LoggedInState.LOGGED_IN)
      val addApplicationSubordinateEmptyNestView = app.injector.instanceOf[AddApplicationSubordinateEmptyNestView]

      addApplicationSubordinateEmptyNestView.render(request, loggedIn, messagesProvider, appConfig, "nav-section", environmentNameService)
    }

    "show the empty nest page when there are no applications when environment is Prod/Sandbox" in new ProdAndET with Setup {

      val appSummaries = Seq()

      val document = Jsoup.parse(renderPage(appSummaries).body)

      elementExistsByText(document, "h1", "Start using our APIs") shouldBe true
    }

    "show the empty nest page when there are no applications when environment is QA/Dev" in new ProdAndET with Setup {
      when(appConfig.nameOfPrincipalEnvironment).thenReturn("QA")
      when(appConfig.nameOfSubordinateEnvironment).thenReturn("Development")

      val appSummaries = Seq()

      val document = Jsoup.parse(renderPage(appSummaries).body)

      elementExistsByText(document, "h1", "Start using our APIs") shouldBe true
    }

  }
}
