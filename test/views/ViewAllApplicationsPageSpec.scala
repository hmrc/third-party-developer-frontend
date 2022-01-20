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

package views

import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.{ManageApplicationsViewModel, ApplicationSummary}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.AccessType
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.{ApplicationId, CollaboratorRole, State, TermsOfUseStatus}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.LoggedInState
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import play.api.test.FakeRequest
import uk.gov.hmrc.time.DateTimeUtils
import utils.ViewHelpers.{elementExistsByText, elementIdentifiedByAttrContainsText}
import utils.WithCSRFAddToken
import utils.DeveloperSessionBuilder
import views.helper.CommonViewSpec
import views.html.{AddApplicationSubordinateEmptyNestView, ManageApplicationsView}
import views.helper.EnvironmentNameService
import uk.gov.hmrc.thirdpartydeveloperfrontend.helpers.DateFormatter
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Environment

class ViewAllApplicationsPageSpec extends CommonViewSpec with WithCSRFAddToken {
  def isGreenAddProductionApplicationButtonVisible(document: Document): Boolean = {
    val href = controllers.addapplication.routes.AddApplication.addApplicationPrincipal().url

    val greenButtons = document.select(s"a[href=$href][class=govuk-button]")

    !greenButtons.isEmpty
  }

  val environmentNameService = new EnvironmentNameService(appConfig)

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

    def hidesGetCredentialsButton()(implicit document: Document) =
      isGreenAddProductionApplicationButtonVisible(document) shouldBe false

    def showsAfterTestingMessage()(implicit document: Document) =
      elementExistsByText(document, "p", s"After testing in ${subordinateWording}, you can apply for ${principal.toLowerCase} credentials.") shouldBe true

    def hidesAfterTestingMessage()(implicit document: Document) =
      elementExistsByText(document, "p", s"After testing in ${subordinateWording}, you can apply for ${principal.toLowerCase} credentials.") shouldBe false

    def showsPriviledAppsMessage()(implicit document: Document) =
      elementExistsByText(document, "h2", "Using privileged application credentials") shouldBe true

    def hidesPriviledAppsMessage()(implicit document: Document) =
      elementExistsByText(document, "h2", "Using privileged application credentials") shouldBe false

    when(appConfig.nameOfPrincipalEnvironment).thenReturn(principal)
    when(appConfig.nameOfSubordinateEnvironment).thenReturn(subordinate)
  }

  "view all applications page" can {

    def renderPage(sandboxAppSummaries: Seq[ApplicationSummary], productionAppSummaries: Seq[ApplicationSummary], upliftableApplicationIds: Set[ApplicationId]) = {
      val request = FakeRequest()
      val loggedIn = DeveloperSessionBuilder("developer@example.com", "firstName", "lastname", loggedInState = LoggedInState.LOGGED_IN)
      val manageApplicationsView = app.injector.instanceOf[ManageApplicationsView]

      manageApplicationsView.render(ManageApplicationsViewModel(sandboxAppSummaries, productionAppSummaries, upliftableApplicationIds, false), request, loggedIn, messagesProvider, appConfig, "nav-section", environmentNameService)
    }

    val applicationId = ApplicationId("1111")
    val appName = "App name 1"
    val appUserRole = CollaboratorRole.ADMINISTRATOR
    val appCreatedOn = DateTimeUtils.now.minusDays(1)
    val appLastAccess = appCreatedOn

    val sandboxAppSummaries = Seq(
        ApplicationSummary(
          applicationId, 
          appName, 
          appUserRole,
          TermsOfUseStatus.NOT_APPLICABLE,
          State.TESTING,
          appLastAccess,
          grantLength,
          false,
          appCreatedOn,
          AccessType.STANDARD,
          Environment.SANDBOX,
          Set.empty
        ))

    val productionAppSummaries = Seq(
      ApplicationSummary(
        applicationId,
        appName,
        appUserRole,
        TermsOfUseStatus.NOT_APPLICABLE,
        State.PRODUCTION,
        appLastAccess,
        grantLength,
        false,
        appCreatedOn,
        AccessType.STANDARD,
        Environment.PRODUCTION,
        Set.empty
      )
    )

    "show the application page when an application exists" should {

      "show the heading when there is a sandbox app" in new ProdAndET with Setup {
        implicit val document = Jsoup.parse(renderPage(sandboxAppSummaries, Seq.empty, Set(applicationId)).body)
        
        showsHeading()
      }

      "show the heading when there is a production app" in new ProdAndET with Setup {
        implicit val document = Jsoup.parse(renderPage(Seq.empty, productionAppSummaries, Set(applicationId)).body)
        
        showsHeading()
      }
    }

    "show the applications page with correct app headings" should {
      "work in Prod/Sandbox with prod and sandbox apps" in new ProdAndET with Setup {
        implicit val document = Jsoup.parse(renderPage(sandboxAppSummaries, productionAppSummaries, Set(applicationId)).body)
        
        showsAppName(appName)
        showsSubordinateAppsHeading()
        showsPrincipalAppsHeading()
      }

      "work in Prod/Sandbox with prod apps" in new ProdAndET with Setup {
        implicit val document = Jsoup.parse(renderPage(Seq.empty, productionAppSummaries, Set(applicationId)).body)
        
        showsAppName(appName)
        hidesSubordinateAppsHeading()
        showsPrincipalAppsHeading()
      }

      "work in Prod/Sandbox with sandbox apps" in new ProdAndET with Setup {
        implicit val document = Jsoup.parse(renderPage(sandboxAppSummaries, Seq.empty, Set(applicationId)).body)
        
        showsAppName(appName)
        showsSubordinateAppsHeading()
        hidesPrincipalAppsHeading()
      }

      "work in Qa/Dev with appropriate heading labels" in new QaAndDev with Setup {
        implicit val document = Jsoup.parse(renderPage(sandboxAppSummaries, productionAppSummaries, Set(applicationId)).body)
        
        showsAppName(appName)
        showsSubordinateAppsHeading()
        showsPrincipalAppsHeading()
      }
    }

    "handling application information" should {
      "show no api called and user role" in new ProdAndET with Setup {
        implicit val document = Jsoup.parse(renderPage(sandboxAppSummaries, Seq.empty, Set(applicationId)).body)
        
        showsAppName(appName)
        elementIdentifiedByAttrContainsText(document, "td", "data-app-lastAccess", "No API called") shouldBe true
        elementIdentifiedByAttrContainsText(document, "td", "data-app-user-role", "Admin") shouldBe true
      }

      "show the last access and user role" in new ProdAndET with Setup {
        val datetime = DateTimeUtils.now
        val datetimeText = DateFormatter.standardFormatter.print(datetime)
        val calledApp = sandboxAppSummaries.map(_.copy(lastAccess = datetime))
        implicit val document = Jsoup.parse(renderPage(calledApp, Seq.empty, Set(applicationId)).body)
        
        showsAppName(appName)
        elementIdentifiedByAttrContainsText(document, "td", "data-app-lastAccess", "No API called") shouldBe false
        elementIdentifiedByAttrContainsText(document, "td", "data-app-lastAccess", datetimeText) shouldBe true

        elementIdentifiedByAttrContainsText(document, "td", "data-app-user-role", "Admin") shouldBe true
      }
    }

    "handle credentials button" should {
      "show when sandbox app is upliftable" in new ProdAndET with Setup {
        implicit val document = Jsoup.parse(renderPage(sandboxAppSummaries, Seq.empty, Set(applicationId)).body)

        showsGetCredentialsButton()
        showsAfterTestingMessage()
      }
      
      "hides when sandbox app is not upliftable" in new ProdAndET with Setup {
        implicit val document = Jsoup.parse(renderPage(sandboxAppSummaries, Seq.empty, Set.empty).body)
        
        hidesGetCredentialsButton()
        hidesAfterTestingMessage()
      }

      "hides when production app is present even when upliftable sandbox app is present" in new ProdAndET with Setup {
        implicit val document = Jsoup.parse(renderPage(sandboxAppSummaries, productionAppSummaries, Set(applicationId)).body)
        
        hidesGetCredentialsButton()
        hidesAfterTestingMessage()
      }
    }

    "handling of privileged applications" should {
      "show using privileged application credentials text if user is a collaborator on at least one privileged application" in new ProdAndET with Setup {
        val priviledgedAppSummaries = productionAppSummaries.map(_.copy(accessType=AccessType.PRIVILEGED))

        implicit val document = Jsoup.parse(renderPage(Seq.empty, priviledgedAppSummaries, Set(applicationId)).body)

        showsAppName(appName)

        showsPriviledAppsMessage()
      }

      "hide using privileged application credentials text if user is not a collaborator on at least one privileged application" in new ProdAndET with Setup {
        implicit val document = Jsoup.parse(renderPage(Seq.empty, productionAppSummaries, Set(applicationId)).body)

        showsAppName(appName)

        hidesPriviledAppsMessage()
      }
    }
  }

  "welcome to your account page" should {

    def renderPage(appSummaries: Seq[ApplicationSummary]) = {
      val request = FakeRequest().withCSRFToken
      val loggedIn = DeveloperSessionBuilder("developer@example.com", "firstName", "lastname", loggedInState = LoggedInState.LOGGED_IN)
      val addApplicationSubordinateEmptyNestView = app.injector.instanceOf[AddApplicationSubordinateEmptyNestView]

      addApplicationSubordinateEmptyNestView.render(request, loggedIn, messagesProvider, appConfig, "nav-section", environmentNameService)
    }
    
    "show the empty nest page when there are no applications when environment is Prod/Sandbox" in new ProdAndET with Setup {
      val appSummaries = Seq()

      val document = Jsoup.parse(renderPage(appSummaries).body)

      elementExistsByText(document, "h1", "Start using our APIs") shouldBe true
    }

    "show the empty nest page when there are no applications when environment is QA/Dev" in new QaAndDev with Setup {
      val appSummaries = Seq()

      val document = Jsoup.parse(renderPage(appSummaries).body)

      elementExistsByText(document, "h1", "Start using our APIs") shouldBe true
    }
  }
}
