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

import play.api.test.FakeRequest

import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models.AccessType
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.{ApplicationNameFixtures, Collaborator, State}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{ApplicationId, ApplicationIdFixtures, Environment}
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.apiplatform.modules.organisations.domain.models.OrganisationName
import uk.gov.hmrc.apiplatform.modules.organisations.submissions.domain.models.{OrganisationAllowList, Submission}
import uk.gov.hmrc.apiplatform.modules.tpd.test.builders.UserBuilder
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.DeveloperSessionBuilder
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.TermsOfUseStatus
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.TermsOfUseInvitation
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.{ApplicationSummary, ManageApplicationsViewModel}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.ViewHelpers.elementExistsById
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithCSRFAddToken

class ViewAllApplicationsPageOrganisationRegistrationSpec extends CommonViewSpec
    with WithCSRFAddToken
    with LocalUserIdTracker
    with DeveloperSessionBuilder
    with UserBuilder
    with FixedClock
    with ApplicationIdFixtures
    with ApplicationNameFixtures
    with uk.gov.hmrc.apiplatform.modules.organisations.submissions.utils.SubmissionsTestData {

  val environmentNameService = new EnvironmentNameService(appConfig)

  trait Setup {

    def showsOrganisationInviteBox()(implicit document: Document): Assertion =
      elementExistsById(document, "organisation-start-registration") shouldBe true

    def hidesOrganisationInviteBox()(implicit document: Document): Assertion =
      elementExistsById(document, "organisation-start-registration") shouldBe false

    def showsContinueOrgRegBox()(implicit document: Document): Assertion =
      elementExistsById(document, "organisation-continue-registration") shouldBe true

    def hidesContinueOrgRegBox()(implicit document: Document): Assertion =
      elementExistsById(document, "organisation-continue-registration") shouldBe false

    def showsViewSubmissionBox()(implicit document: Document): Assertion =
      elementExistsById(document, "organisation-view-submission") shouldBe true

    def hidesViewSubmissionBox()(implicit document: Document): Assertion =
      elementExistsById(document, "organisation-view-submission") shouldBe false

    def showsResubmitSubmissionBox()(implicit document: Document): Assertion =
      elementExistsById(document, "organisation-resubmit") shouldBe true

    def hidesResubmitSubmissionBox()(implicit document: Document): Assertion =
      elementExistsById(document, "organisation-resubmit") shouldBe false

    def showsRegistrationSuccessfulBox()(implicit document: Document): Assertion =
      elementExistsById(document, "organisation-registration-successful") shouldBe true

    def hidesRegistrationSuccessfulBox()(implicit document: Document): Assertion =
      elementExistsById(document, "organisation-registration-successful") shouldBe false

    val loggedIn  = buildUser("developer@example.com".toLaxEmail, "firstName", "lastname").loggedIn
    val allowList = Some(OrganisationAllowList(loggedIn.developer.userId, OrganisationName("Test Organisation"), "requestedBy", instant))

    def renderPage(
        sandboxAppSummaries: Seq[ApplicationSummary],
        productionAppSummaries: Seq[ApplicationSummary],
        upliftableApplicationIds: Set[ApplicationId],
        termsOfUseInvitations: List[TermsOfUseInvitation] = List.empty,
        productionApplicationSubmissions: List[uk.gov.hmrc.apiplatform.modules.submissions.domain.models.Submission] = List.empty,
        organisationAllowList: Option[OrganisationAllowList] = None,
        organisationSubmission: Option[Submission] = None
      ) = {
      val request                = FakeRequest()
      val manageApplicationsView = app.injector.instanceOf[ManageApplicationsView]

      manageApplicationsView.render(
        ManageApplicationsViewModel(
          sandboxAppSummaries,
          productionAppSummaries,
          upliftableApplicationIds,
          false,
          termsOfUseInvitations,
          productionApplicationSubmissions,
          organisationAllowList,
          organisationSubmission
        ),
        request,
        loggedIn,
        messagesProvider,
        appConfig,
        "nav-section",
        environmentNameService
      )
    }

    val appUserRole   = Collaborator.Roles.ADMINISTRATOR
    val appCreatedOn  = instant.minus(1, DAYS)
    val appLastAccess = Some(appCreatedOn)
    val applicationId = applicationIdOne

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

    val answering1 = Submission.addStatusHistory(Submission.Status.Answering(instant, false))(aSubmission)
    val answering2 = Submission.updateLatestAnswersTo(samplePassAnswersToQuestions)(answering1)
    val answered   = Submission.addStatusHistory(Submission.Status.Answering(instant, true))(answering2)
    val submitted  = Submission.submit(instant, "bob@example.com")(answered)
    val declined   = Submission.decline(instant, "bob@example.com", "comments")(submitted)
    val granted    = Submission.grant(instant, "bob@example.com", Some("comments"), Some(""))(submitted)
  }

  "Organisation box on view all applications page" should {

    "show Start organisation registration banner when no submission exists and user is in the allowlist" in new Setup {
      implicit val document: Document = Jsoup.parse(renderPage(sandboxAppSummaries, productionAppSummaries, Set(applicationId), organisationAllowList = allowList).body)

      showsOrganisationInviteBox()
      hidesContinueOrgRegBox()
      hidesViewSubmissionBox()
      hidesResubmitSubmissionBox()
      hidesRegistrationSuccessfulBox()
    }

    "show Continue registration banner when beginning to answer questions" in new Setup {
      implicit val document: Document =
        Jsoup.parse(renderPage(sandboxAppSummaries, productionAppSummaries, Set(applicationId), organisationAllowList = allowList, organisationSubmission = Some(answering1)).body)

      hidesOrganisationInviteBox()
      showsContinueOrgRegBox()
      hidesViewSubmissionBox()
      hidesResubmitSubmissionBox()
      hidesRegistrationSuccessfulBox()
    }

    "show Continue registration banner when nearly finished answering questions" in new Setup {
      implicit val document: Document =
        Jsoup.parse(renderPage(sandboxAppSummaries, productionAppSummaries, Set(applicationId), organisationAllowList = allowList, organisationSubmission = Some(answering2)).body)

      hidesOrganisationInviteBox()
      showsContinueOrgRegBox()
      hidesViewSubmissionBox()
      hidesResubmitSubmissionBox()
      hidesRegistrationSuccessfulBox()
    }

    "show View Submission banner when submission has been submitted" in new Setup {
      implicit val document: Document =
        Jsoup.parse(renderPage(sandboxAppSummaries, productionAppSummaries, Set(applicationId), organisationAllowList = allowList, organisationSubmission = Some(submitted)).body)

      hidesOrganisationInviteBox()
      hidesContinueOrgRegBox()
      showsViewSubmissionBox()
      hidesResubmitSubmissionBox()
      hidesRegistrationSuccessfulBox()
    }

    "show Re-submit banner when submission has been declined" in new Setup {
      implicit val document: Document =
        Jsoup.parse(renderPage(sandboxAppSummaries, productionAppSummaries, Set(applicationId), organisationAllowList = allowList, organisationSubmission = Some(declined)).body)

      hidesOrganisationInviteBox()
      hidesContinueOrgRegBox()
      hidesViewSubmissionBox()
      showsResubmitSubmissionBox()
      hidesRegistrationSuccessfulBox()
    }

    "show Organisation registration successful banner when submission has been granted" in new Setup {
      implicit val document: Document =
        Jsoup.parse(renderPage(sandboxAppSummaries, productionAppSummaries, Set(applicationId), organisationAllowList = allowList, organisationSubmission = Some(granted)).body)

      hidesOrganisationInviteBox()
      hidesContinueOrgRegBox()
      hidesViewSubmissionBox()
      hidesResubmitSubmissionBox()
      showsRegistrationSuccessfulBox()
    }

    "hide all organisation registration banners when no allowlist or submission" in new Setup {
      implicit val document: Document = Jsoup.parse(renderPage(sandboxAppSummaries, productionAppSummaries, Set(applicationId)).body)

      hidesOrganisationInviteBox()
      hidesContinueOrgRegBox()
      hidesViewSubmissionBox()
      hidesResubmitSubmissionBox()
      hidesRegistrationSuccessfulBox()
    }
  }
}
