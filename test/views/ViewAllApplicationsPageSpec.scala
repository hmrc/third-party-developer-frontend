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

import java.time.temporal.ChronoUnit.DAYS

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.scalatest.Assertion
import views.helper.{CommonViewSpec, EnvironmentNameService}
import views.html.ManageApplicationsView
import views.html.noapplications.StartUsingRestApisView

import play.api.test.FakeRequest

import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models.AccessType
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.{ApplicationName, ApplicationNameFixtures, Collaborator, State}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{ApplicationId, Environment}
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.apiplatform.modules.submissions.SubmissionsTestData
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models.Submission
import uk.gov.hmrc.apiplatform.modules.tpd.test.builders.UserBuilder
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.DeveloperSessionBuilder
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.addapplication.routes.{AddApplication => AddApplicationRoutes}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.TermsOfUseStatus
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.TermsOfUseInvitation
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.TermsOfUseInvitationState.EMAIL_SENT
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.{ApplicationSummary, ManageApplicationsViewModel}
import uk.gov.hmrc.thirdpartydeveloperfrontend.helpers.DateFormatter
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.ViewHelpers.{elementExistsByText, elementIdentifiedByAttrContainsText}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithCSRFAddToken

class ViewAllApplicationsPageSpec extends CommonViewSpec
    with WithCSRFAddToken
    with LocalUserIdTracker
    with DeveloperSessionBuilder
    with UserBuilder
    with FixedClock
    with SubmissionsTestData
    with ApplicationNameFixtures {

  def isGreenAddProductionApplicationButtonVisible(document: Document): Boolean = {
    val href = AddApplicationRoutes.addApplicationPrincipal().url

    val greenButtons = document.select(s"a[href=$href][class=govuk-button]")

    !greenButtons.isEmpty
  }

  val environmentNameService = new EnvironmentNameService(appConfig)

  trait EnvNames {
    def principalCapitalized: String   = principal.capitalize
    def subordinateCapitalized: String = subordinate.capitalize
    def principal: String
    def subordinate: String
    def subordinateWording: String
  }

  trait ProdAndET extends EnvNames {
    val principal          = "Production"
    val subordinate        = "Sandbox"
    val subordinateWording = "the sandbox"
  }

  trait QaAndDev extends EnvNames {
    val principal          = "QA"
    val subordinate        = "Development"
    val subordinateWording = "development"
  }

  trait Setup {
    self: EnvNames =>

    def showsHeading()(implicit document: Document): Assertion =
      elementExistsByText(document, "h1", "View all applications") shouldBe true

    def showsAppName(appName: ApplicationName)(implicit document: Document): Assertion =
      elementIdentifiedByAttrContainsText(document, "a", "data-app-name", appName.value) shouldBe true

    def showsSubordinateAppsHeading()(implicit document: Document): Assertion =
      elementExistsByText(document, "th", s"$subordinateCapitalized applications") shouldBe true

    def showsPrincipalAppsHeading()(implicit document: Document): Assertion =
      elementExistsByText(document, "th", s"$principalCapitalized applications") shouldBe true

    def showsTermsOfUseOutstandingBox()(implicit document: Document): Assertion =
      elementExistsByText(document, "h2", "Important") shouldBe true

    def hidesTermsOfUseOutstandingBox()(implicit document: Document): Assertion =
      elementExistsByText(document, "h2", "Important") shouldBe false

    def showsTermsOfUseReviewBox()(implicit document: Document): Assertion =
      elementExistsByText(document, "h2", "Terms of use") shouldBe true

    def hidesTermsOfUseReviewBox()(implicit document: Document): Assertion =
      elementExistsByText(document, "h2", "Terms of use") shouldBe false

    def hidesSubordinateAppsHeading()(implicit document: Document): Assertion =
      elementExistsByText(document, "th", s"$subordinateCapitalized applications") shouldBe false

    def hidesPrincipalAppsHeading()(implicit document: Document): Assertion =
      elementExistsByText(document, "th", s"$principalCapitalized applications") shouldBe false

    def showsGetCredentialsButton()(implicit document: Document): Assertion =
      isGreenAddProductionApplicationButtonVisible(document) shouldBe true

    def hidesGetCredentialsButton()(implicit document: Document): Assertion =
      isGreenAddProductionApplicationButtonVisible(document) shouldBe false

    def showsAfterTestingMessage()(implicit document: Document): Assertion =
      elementExistsByText(document, "p", s"After testing in ${subordinateWording}, you can apply for ${principal.toLowerCase} credentials.") shouldBe true

    def hidesAfterTestingMessage()(implicit document: Document): Assertion =
      elementExistsByText(document, "p", s"After testing in ${subordinateWording}, you can apply for ${principal.toLowerCase} credentials.") shouldBe false

    def showsPriviledAppsMessage()(implicit document: Document): Assertion =
      elementExistsByText(document, "h2", "Using privileged application credentials") shouldBe true

    def hidesPriviledAppsMessage()(implicit document: Document): Assertion =
      elementExistsByText(document, "h2", "Using privileged application credentials") shouldBe false

    when(appConfig.nameOfPrincipalEnvironment).thenReturn(principal)
    when(appConfig.nameOfSubordinateEnvironment).thenReturn(subordinate)
  }

  "view all applications page" can {

    def renderPage(
        sandboxAppSummaries: Seq[ApplicationSummary],
        productionAppSummaries: Seq[ApplicationSummary],
        upliftableApplicationIds: Set[ApplicationId],
        termsOfUseInvitations: List[TermsOfUseInvitation] = List.empty,
        productionApplicationSubmissions: List[Submission] = List.empty
      ) = {
      val request                = FakeRequest()
      val loggedIn               = buildUser("developer@example.com".toLaxEmail, "firstName", "lastname").loggedIn
      val manageApplicationsView = app.injector.instanceOf[ManageApplicationsView]

      manageApplicationsView.render(
        ManageApplicationsViewModel(sandboxAppSummaries, productionAppSummaries, upliftableApplicationIds, false, termsOfUseInvitations, productionApplicationSubmissions),
        request,
        loggedIn,
        messagesProvider,
        appConfig,
        "nav-section",
        environmentNameService
      )
    }

    // val appName       = "App name 1"
    val appUserRole   = Collaborator.Roles.ADMINISTRATOR
    val appCreatedOn  = instant.minus(1, DAYS)
    val appLastAccess = Some(appCreatedOn)

    val sandboxAppSummaries = Seq(
      ApplicationSummary(
        applicationId,
        appNameOne,
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
      )
    )

    val productionAppSummaries = Seq(
      ApplicationSummary(
        applicationId,
        appNameOne,
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
        implicit val document: Document = Jsoup.parse(renderPage(sandboxAppSummaries, Seq.empty, Set(applicationId)).body)

        showsHeading()
      }

      "show the heading when there is a production app" in new ProdAndET with Setup {
        implicit val document: Document = Jsoup.parse(renderPage(Seq.empty, productionAppSummaries, Set(applicationId)).body)

        showsHeading()
      }
    }

    "show the applications page with correct app headings" should {
      "work in Prod/Sandbox with prod and sandbox apps" in new ProdAndET with Setup {
        implicit val document: Document = Jsoup.parse(renderPage(sandboxAppSummaries, productionAppSummaries, Set(applicationId)).body)

        showsAppName(appNameOne)
        showsSubordinateAppsHeading()
        showsPrincipalAppsHeading()
      }

      "work in Prod/Sandbox with prod apps" in new ProdAndET with Setup {
        implicit val document: Document = Jsoup.parse(renderPage(Seq.empty, productionAppSummaries, Set(applicationId)).body)

        showsAppName(appNameOne)
        hidesSubordinateAppsHeading()
        showsPrincipalAppsHeading()
      }

      "work in Prod/Sandbox with sandbox apps" in new ProdAndET with Setup {
        implicit val document: Document = Jsoup.parse(renderPage(sandboxAppSummaries, Seq.empty, Set(applicationId)).body)

        showsAppName(appNameOne)
        showsSubordinateAppsHeading()
        hidesPrincipalAppsHeading()
      }

      "work in Qa/Dev with appropriate heading labels" in new QaAndDev with Setup {
        implicit val document: Document = Jsoup.parse(renderPage(sandboxAppSummaries, productionAppSummaries, Set(applicationId)).body)

        showsAppName(appNameOne)
        showsSubordinateAppsHeading()
        showsPrincipalAppsHeading()
      }
    }

    "show the applications page with outstanding terms of use box" should {
      "work in Qa/Dev with invites to display" in new QaAndDev with Setup {
        val invites: List[TermsOfUseInvitation] = List(TermsOfUseInvitation(applicationId, instant, instant, instant, None, EMAIL_SENT))

        implicit val document: Document = Jsoup.parse(renderPage(sandboxAppSummaries, productionAppSummaries, Set(applicationId), invites).body)

        showsAppName(appNameOne)
        showsSubordinateAppsHeading()
        showsPrincipalAppsHeading()
        showsTermsOfUseOutstandingBox()
        hidesTermsOfUseReviewBox()
      }

      "work in Qa/Dev with no invites to display" in new QaAndDev with Setup {
        implicit val document: Document = Jsoup.parse(renderPage(sandboxAppSummaries, productionAppSummaries, Set(applicationId), List.empty).body)

        showsAppName(appNameOne)
        showsSubordinateAppsHeading()
        showsPrincipalAppsHeading()
        hidesTermsOfUseOutstandingBox()
        hidesTermsOfUseReviewBox()
      }
    }

    "show the applications page with no outstanding terms of use box" should {
      "work in Qa/Dev with an invite that has granted submissions" in new QaAndDev with Setup {
        val invites: List[TermsOfUseInvitation] = List(TermsOfUseInvitation(applicationId, instant, instant, instant, None, EMAIL_SENT))
        val submissions: List[Submission]       = List(grantedSubmission)

        implicit val document: Document = Jsoup.parse(renderPage(sandboxAppSummaries, productionAppSummaries, Set(applicationId), invites, submissions).body)

        showsAppName(appNameOne)
        showsSubordinateAppsHeading()
        showsPrincipalAppsHeading()
        hidesTermsOfUseOutstandingBox()
        hidesTermsOfUseReviewBox()
      }
    }

    "show the applications page with review terms of use box" should {
      "work in Qa/Dev with submissions in review" in new QaAndDev with Setup {
        val invites: List[TermsOfUseInvitation] = List(TermsOfUseInvitation(applicationId, instant, instant, instant, None, EMAIL_SENT))
        val submissions: List[Submission]       = List(submittedSubmission)

        implicit val document: Document = Jsoup.parse(renderPage(sandboxAppSummaries, productionAppSummaries, Set(applicationId), invites, submissions).body)

        showsAppName(appNameOne)
        showsSubordinateAppsHeading()
        showsPrincipalAppsHeading()
        hidesTermsOfUseOutstandingBox()
        showsTermsOfUseReviewBox()
      }

      "work in Qa/Dev with no submissions in review" in new QaAndDev with Setup {
        val invites: List[TermsOfUseInvitation] = List(TermsOfUseInvitation(applicationId, instant, instant, instant, None, EMAIL_SENT))

        implicit val document: Document = Jsoup.parse(renderPage(sandboxAppSummaries, productionAppSummaries, Set(applicationId), invites, List.empty).body)

        showsAppName(appNameOne)
        showsSubordinateAppsHeading()
        showsPrincipalAppsHeading()
        showsTermsOfUseOutstandingBox()
        hidesTermsOfUseReviewBox()
      }
    }

    "handling application information" should {
      "show no api called and user role" in new ProdAndET with Setup {
        implicit val document: Document = Jsoup.parse(renderPage(sandboxAppSummaries, Seq.empty, Set(applicationId)).body)

        showsAppName(appNameOne)
        elementIdentifiedByAttrContainsText(document, "td", "data-app-lastAccess", "No API called") shouldBe true
        elementIdentifiedByAttrContainsText(document, "td", "data-app-user-role", "Admin") shouldBe true
      }

      "show the last access and user role" in new ProdAndET with Setup {
        val datetimeText: String               = DateFormatter.standardFormatter.format(instant)
        val calledApp: Seq[ApplicationSummary] = sandboxAppSummaries.map(_.copy(lastAccess = Some(instant)))
        implicit val document: Document        = Jsoup.parse(renderPage(calledApp, Seq.empty, Set(applicationId)).body)

        showsAppName(appNameOne)
        elementIdentifiedByAttrContainsText(document, "td", "data-app-lastAccess", "No API called") shouldBe false
        elementIdentifiedByAttrContainsText(document, "td", "data-app-lastAccess", datetimeText) shouldBe true

        elementIdentifiedByAttrContainsText(document, "td", "data-app-user-role", "Admin") shouldBe true
      }
    }

    "handle credentials button" should {
      "show when sandbox app is upliftable" in new ProdAndET with Setup {
        implicit val document: Document = Jsoup.parse(renderPage(sandboxAppSummaries, Seq.empty, Set(applicationId)).body)

        showsGetCredentialsButton()
        showsAfterTestingMessage()
      }

      "hides when sandbox app is not upliftable" in new ProdAndET with Setup {
        implicit val document: Document = Jsoup.parse(renderPage(sandboxAppSummaries, Seq.empty, Set.empty).body)

        hidesGetCredentialsButton()
        hidesAfterTestingMessage()
      }

      "hides when production app is present even when upliftable sandbox app is present" in new ProdAndET with Setup {
        implicit val document: Document = Jsoup.parse(renderPage(sandboxAppSummaries, productionAppSummaries, Set(applicationId)).body)

        hidesGetCredentialsButton()
        hidesAfterTestingMessage()
      }
    }

    "handling of privileged applications" should {
      "show using privileged application credentials text if user is a collaborator on at least one privileged application" in new ProdAndET with Setup {
        val priviledgedAppSummaries: Seq[ApplicationSummary] = productionAppSummaries.map(_.copy(accessType = AccessType.PRIVILEGED))

        implicit val document: Document = Jsoup.parse(renderPage(Seq.empty, priviledgedAppSummaries, Set(applicationId)).body)

        showsAppName(appNameOne)

        showsPriviledAppsMessage()
      }

      "hide using privileged application credentials text if user is not a collaborator on at least one privileged application" in new ProdAndET with Setup {
        implicit val document: Document = Jsoup.parse(renderPage(Seq.empty, productionAppSummaries, Set(applicationId)).body)

        showsAppName(appNameOne)

        hidesPriviledAppsMessage()
      }
    }
  }

  "welcome to your account page" should {

    def renderPage(appSummaries: Seq[ApplicationSummary]) = {
      val request                                = FakeRequest().withCSRFToken
      val loggedIn                               = buildUser("developer@example.com".toLaxEmail, "firstName", "lastname").loggedIn
      val addApplicationSubordinateEmptyNestView = app.injector.instanceOf[StartUsingRestApisView]

      addApplicationSubordinateEmptyNestView.render(request, loggedIn, messagesProvider, appConfig, "nav-section", environmentNameService)
    }

    "show the empty nest page when there are no applications when environment is Prod/Sandbox" in new ProdAndET with Setup {
      val appSummaries: Seq[Nothing] = Seq()

      val document: Document = Jsoup.parse(renderPage(appSummaries).body)

      elementExistsByText(document, "h1", "Start using our REST APIs") shouldBe true
    }

    "show the empty nest page when there are no applications when environment is QA/Dev" in new QaAndDev with Setup {
      val appSummaries: Seq[Nothing] = Seq()

      val document: Document = Jsoup.parse(renderPage(appSummaries).body)

      elementExistsByText(document, "h1", "Start using our REST APIs") shouldBe true
    }
  }
}
