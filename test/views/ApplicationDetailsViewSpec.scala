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

import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

import org.jsoup.Jsoup
import org.jsoup.nodes.{Document, Element}
import views.helper.CommonViewSpec
import views.html.manageapplication.ApplicationDetailsView

import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.twirl.api.HtmlFormat.Appendable

import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models.Access
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.{
  ApplicationTokenData,
  ApplicationWithCollaborators,
  ApplicationWithCollaboratorsFixtures,
  CheckInformation,
  TermsOfUseAgreement
}
import uk.gov.hmrc.apiplatform.modules.applications.submissions.domain.models.{ResponsibleIndividualFixtures, TermsOfUseAcceptanceData}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.Environment
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.UserSession
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.Details.{Agreement, TermsOfUseViewModel}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.routes
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.TermsOfUseV2State
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.ApplicationViewModel
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.services.TermsOfUseService.TermsOfUseAgreementDetails
import uk.gov.hmrc.thirdpartydeveloperfrontend.helpers.DateFormatter
import uk.gov.hmrc.thirdpartydeveloperfrontend.testdata.CommonSessionFixtures
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithCSRFAddToken

class ApplicationDetailsViewSpec
    extends CommonViewSpec
    with WithCSRFAddToken
    with CommonSessionFixtures
    with ApplicationWithCollaboratorsFixtures
    with ResponsibleIndividualFixtures
    with FixedClock {

  implicit class AppAugment(val app: ApplicationWithCollaborators) {
    final def withCheckInformation(checkInformation: CheckInformation): ApplicationWithCollaborators = app.modify(_.copy(checkInformation = Some(checkInformation)))
  }

  val applicationDetailsView = app.injector.instanceOf[ApplicationDetailsView]

  case class Page(doc: Appendable) {
    lazy val body: Document                     = Jsoup.parse(doc.body)
    lazy val environmentName: Element           = body.getElementById("environment")
    lazy val applicationId: Element             = body.getElementById("applicationId")
    lazy val descriptionCell: Element           = body.getElementById("description")
    lazy val changeAppDescriptionLink: Element  = body.getElementById("changeAppDetailsLink")
    lazy val applicationNameChangeLink: Element = body.getElementById("changeAppNameLink")
    lazy val warning: Element                   = body.getElementById("terms-of-use-header")

    lazy val termsOfUse: Element                = body.getElementById("termsOfUse")
    lazy val termsOfUseAgreementV1: Element     = body.getElementById("termsOfUseAgreementV1")
    lazy val termsOfUseLinkV1: Element          = body.getElementById("termsOfUseLinkV1")
    lazy val termsOfUseAgreementV2: Element     = body.getElementById("termsOfUseAgreementV2")
    lazy val termsOfUseLinkV2: Element          = body.getElementById("termsOfUseLinkV2")
    lazy val responsibleIndividual: Element     = body.getElementById("responsibleIndividual")
    lazy val responsibleIndividualName: Element = body.getElementById("responsibleIndividualName")
    lazy val responsibleIndividualLink: Element = body.getElementById("changeResponsibleIndividual")

    lazy val termsOfUseNotAgreed: Element         = body.getElementById("termsOfUseNotAgreed")
    lazy val termsOfUseStartLink: Element         = body.getElementById("termsOfUseStartLink")
    lazy val termsOfUseV2StartLink: Element       = body.getElementById("termsOfUseV2StartLink")
    lazy val termsOfUseStartedDetails: Element    = body.getElementById("termsOfUseStartedDetails")
    lazy val termsOfUseContinueLink: Element      = body.getElementById("termsOfUseContinueLink")
    lazy val termsOfUseSubmittedDetails: Element  = body.getElementById("termsOfUseSubmittedDetails")
    lazy val termsOfUseSubmittedViewLink: Element = body.getElementById("termsOfUseSubmittedViewLink")
    lazy val termsOfUseV2UpliftDetails: Element   = body.getElementById("termsOfUseV2UpliftDetails")
    lazy val termsOfUseInReviewDetails: Element   = body.getElementById("termsOfUseInReviewDetails")
    lazy val termsOfUseInReviewViewLink: Element  = body.getElementById("termsOfUseInReviewViewLink")

    lazy val privacyPolicy: Element                     = body.getElementById("privacyPolicy")
    lazy val changePrivacyPolicyLocationLink: Element   = body.getElementById("changePrivacyPolicy")
    lazy val termsAndConditions: Element                = body.getElementById("termsAndConditions")
    lazy val changeTermsConditionsLocationLink: Element = body.getElementById("changeTermsAndConditions")
    lazy val changingAppDetailsAdminList: Element       = body.getElementById("changingAppDetailsAdminList")
  }

  val termsOfUseViewModel = TermsOfUseViewModel(true, true, Some(Agreement("user@example.com", instant)))
  val sandboxApp          = standardApp.inSandbox()
  val prodApp             = standardApp.withEnvironment(Environment.PRODUCTION)

  val prodAppWithRespIndAndV2TermsOfUse = prodApp.withAccess(standardAccessWithSubmission).withToken(ApplicationTokenData.one)
    .modify(_.copy(description = Some("Some App Description")))

  val v2Agreement = TermsOfUseAgreementDetails(
    TermsOfUseAcceptanceData.one.responsibleIndividual.emailAddress,
    Some(TermsOfUseAcceptanceData.one.responsibleIndividual.fullName.value),
    TermsOfUseAcceptanceData.one.dateTime,
    None
  )

  val v2AgreementWording =
    s"${v2Agreement.name.getOrElse(v2Agreement.emailAddress)} agreed to version 2 of the terms of use on ${DateFormatter.formatTwoDigitDay(v2Agreement.date)}."

  trait LoggedInUserIsAdmin {
    implicit val loggedIn: UserSession = adminSession
  }

  trait LoggedInUserIsDev {
    implicit val loggedIn: UserSession = devSession
  }

  "Application details view" when {
    implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
    implicit val navSection: String                           = "details"

    "rendering Environment " when {
      "managing a principal application" should {

        "Show Production when environment is Production" in new LoggedInUserIsDev {
          when(appConfig.nameOfPrincipalEnvironment).thenReturn("Production")
          when(appConfig.nameOfSubordinateEnvironment).thenReturn("Sandbox")
          val page = Page(applicationDetailsView(ApplicationViewModel(prodApp, hasSubscriptionsFields = false, hasPpnsFields = false), List.empty, None, termsOfUseViewModel))
          page.environmentName.text shouldBe "Production"
        }
        "Show QA when environment is QA" in new LoggedInUserIsDev {
          when(appConfig.nameOfPrincipalEnvironment).thenReturn("QA")
          when(appConfig.nameOfSubordinateEnvironment).thenReturn("Development")
          val page = Page(applicationDetailsView(ApplicationViewModel(prodApp, hasSubscriptionsFields = false, hasPpnsFields = false), List.empty, None, termsOfUseViewModel))
          page.environmentName.text shouldBe "QA"
        }
      }

      "managing a subordinate application" should {

        "Show Sandbox when environment is Sandbox" in new LoggedInUserIsDev {
          when(appConfig.nameOfPrincipalEnvironment).thenReturn("Production")
          when(appConfig.nameOfSubordinateEnvironment).thenReturn("Sandbox")
          val page = Page(applicationDetailsView(ApplicationViewModel(sandboxApp, hasSubscriptionsFields = false, hasPpnsFields = false), List.empty, None, termsOfUseViewModel))
          page.environmentName.text shouldBe "Sandbox"
        }
        "Show Development when environment is Development" in new LoggedInUserIsDev {
          when(appConfig.nameOfPrincipalEnvironment).thenReturn("QA")
          when(appConfig.nameOfSubordinateEnvironment).thenReturn("Development")
          val page = Page(applicationDetailsView(ApplicationViewModel(sandboxApp, hasSubscriptionsFields = false, hasPpnsFields = false), List.empty, None, termsOfUseViewModel))
          page.environmentName.text shouldBe "Development"
        }
      }
    }

    "rendering Application ID " should {
      "Show Production when environment is Production" in new LoggedInUserIsDev {
        val page = Page(applicationDetailsView(ApplicationViewModel(prodApp, hasSubscriptionsFields = false, hasPpnsFields = false), List.empty, None, termsOfUseViewModel))
        page.applicationId.text shouldBe prodApp.id.toString
      }
    }

    "showing Change links for Privacy Policy and Terms & Conditions locations" when {
      val notSet = "Not set"
      "managing a sandbox application" should {

        val termsOfUseViewModelForSandboxApp = termsOfUseViewModel.copy(required = false)

        "Show Change links when a developer" in new LoggedInUserIsDev {
          val page =
            Page(applicationDetailsView(ApplicationViewModel(sandboxApp, hasSubscriptionsFields = false, hasPpnsFields = false), List.empty, None, termsOfUseViewModelForSandboxApp))

          page.privacyPolicy.text shouldBe notSet
          page.termsAndConditions.text shouldBe notSet

          page.changePrivacyPolicyLocationLink should not be null
          page.changePrivacyPolicyLocationLink.text shouldBe "Change"
          page.changePrivacyPolicyLocationLink.attr("href") should include(routes.Details.changeDetails(sandboxApp.id).url)

          page.changeTermsConditionsLocationLink should not be null
          page.changeTermsConditionsLocationLink.text shouldBe "Change"
          page.changeTermsConditionsLocationLink.attr("href") should include(routes.Details.changeDetails(sandboxApp.id).url)
        }

        "Show Change links when an admin" in new LoggedInUserIsAdmin {

          val page =
            Page(applicationDetailsView(ApplicationViewModel(sandboxApp, hasSubscriptionsFields = false, hasPpnsFields = false), List.empty, None, termsOfUseViewModelForSandboxApp))

          page.privacyPolicy.text shouldBe notSet
          page.termsAndConditions.text shouldBe notSet

          page.changePrivacyPolicyLocationLink should not be null
          page.changePrivacyPolicyLocationLink.text shouldBe "Change"
          page.changePrivacyPolicyLocationLink.attr("href") should include(routes.Details.changeDetails(sandboxApp.id).url)

          page.changeTermsConditionsLocationLink should not be null
          page.changeTermsConditionsLocationLink.text shouldBe "Change"
          page.changeTermsConditionsLocationLink.attr("href") should include(routes.Details.changeDetails(sandboxApp.id).url)
        }
      }
      "managing a production application" should {

        "show nothing when a developer" in new LoggedInUserIsDev {
          val page = Page(applicationDetailsView(ApplicationViewModel(prodApp, hasSubscriptionsFields = false, hasPpnsFields = false), List.empty, None, termsOfUseViewModel))

          page.privacyPolicy.text shouldBe notSet
          page.changePrivacyPolicyLocationLink shouldBe null
          page.termsAndConditions.text shouldBe notSet
          page.changeTermsConditionsLocationLink shouldBe null
        }

        "show Change links when an admin" in new LoggedInUserIsAdmin {
          val page = Page(applicationDetailsView(ApplicationViewModel(prodApp, hasSubscriptionsFields = false, hasPpnsFields = false), List.empty, None, termsOfUseViewModel))

          page.changePrivacyPolicyLocationLink should not be null
          page.changePrivacyPolicyLocationLink.text shouldBe "Change"
          page.changePrivacyPolicyLocationLink.attr("href") should include(routes.Details.updatePrivacyPolicyLocation(prodApp.id).url)

          page.changeTermsConditionsLocationLink should not be null
          page.changeTermsConditionsLocationLink.text shouldBe "Change"
          page.changeTermsConditionsLocationLink.attr("href") should include(routes.Details.updateTermsAndConditionsLocation(prodApp.id).url)
        }
      }
    }

    "showing Terms of Use details" when {
      "managing a sandbox application" should {
        val termsOfUseViewModelForSandboxApp = termsOfUseViewModel.copy(required = false)

        "show nothing when a developer" in new LoggedInUserIsDev {
          val page =
            Page(applicationDetailsView(ApplicationViewModel(sandboxApp, hasSubscriptionsFields = false, hasPpnsFields = false), List.empty, None, termsOfUseViewModelForSandboxApp))

          page.termsOfUse shouldBe null
        }

        "show nothing when an admin" in new LoggedInUserIsAdmin {
          val page =
            Page(applicationDetailsView(ApplicationViewModel(sandboxApp, hasSubscriptionsFields = false, hasPpnsFields = false), List.empty, None, termsOfUseViewModelForSandboxApp))

          page.termsOfUse shouldBe null
        }
      }

      "managing a production application" when {
        "the app is a privileged app" should {
          val termsOfUseViewModelForPrivApp = termsOfUseViewModel.copy(required = false)
          val application                   = prodApp.withAccess(Access.Privileged())

          "show nothing when a developer" in new LoggedInUserIsDev {
            val page =
              Page(applicationDetailsView(ApplicationViewModel(application, hasSubscriptionsFields = false, hasPpnsFields = false), List.empty, None, termsOfUseViewModelForPrivApp))

            page.termsOfUse shouldBe null
          }

          "show nothing when an admin" in new LoggedInUserIsAdmin {
            val page =
              Page(applicationDetailsView(ApplicationViewModel(application, hasSubscriptionsFields = false, hasPpnsFields = false), List.empty, None, termsOfUseViewModelForPrivApp))

            page.termsOfUse shouldBe null
          }
        }

        "the app is an ROPC app" should {
          val termsOfUseViewModelForRopcApp = termsOfUseViewModel.copy(required = false)
          val application                   = prodApp.withAccess(Access.Ropc())

          "show nothing when a developer" in new LoggedInUserIsDev {
            val page =
              Page(applicationDetailsView(ApplicationViewModel(application, hasSubscriptionsFields = false, hasPpnsFields = false), List.empty, None, termsOfUseViewModelForRopcApp))

            page.termsOfUse shouldBe null
          }

          "show nothing when an admin" in new LoggedInUserIsAdmin {
            val page =
              Page(applicationDetailsView(ApplicationViewModel(application, hasSubscriptionsFields = false, hasPpnsFields = false), List.empty, None, termsOfUseViewModelForRopcApp))

            page.termsOfUse shouldBe null
          }
        }

        "the app is a standard app" when {

          "the user is a developer" should {
            "have no link to read and agree when the terms of use have not been agreed" in new LoggedInUserIsDev {
              val checkInformation             = CheckInformation(termsOfUseAgreements = List.empty)
              val application                  = prodApp.withCheckInformation(checkInformation)
              val termsOfUseViewModelNotAgreed = termsOfUseViewModel.copy(agreement = None)

              val page =
                Page(applicationDetailsView(ApplicationViewModel(application, hasSubscriptionsFields = false, hasPpnsFields = false), List.empty, None, termsOfUseViewModelNotAgreed))

              page.termsOfUseAgreementV1 shouldBe null
              page.termsOfUseLinkV1 shouldBe null
              page.termsOfUseAgreementV2 shouldBe null
              page.termsOfUseLinkV2 shouldBe null
            }

            "show V1 agreement details and have no link to read when the terms of use have been agreed" in new LoggedInUserIsDev {
              val emailAddress      = "user@example.com".toLaxEmail
              val expectedTimeStamp = DateTimeFormatter.ofPattern("dd MMMM yyyy").withZone(ZoneOffset.UTC).format(instant)
              val version           = "1.0"
              val checkInformation  = CheckInformation(termsOfUseAgreements = List(TermsOfUseAgreement(emailAddress, instant, version)))
              val application       = prodApp.withCheckInformation(checkInformation)

              val page =
                Page(applicationDetailsView(ApplicationViewModel(application, hasSubscriptionsFields = false, hasPpnsFields = false), List.empty, None, termsOfUseViewModel))

              page.termsOfUseAgreementV1.text shouldBe s"${emailAddress.text} agreed to version 1 of the terms of use on $expectedTimeStamp."
              page.termsOfUseLinkV1 shouldBe null
            }

            "show V2 agreement details and have no link to read when the terms of use have been agreed" in new LoggedInUserIsDev {

              val termsOfUseViewModelV2 = TermsOfUseViewModel(true, false, Some(Agreement(v2Agreement.name.getOrElse(v2Agreement.emailAddress.toString()), v2Agreement.date)))
              val page                  =
                Page(applicationDetailsView(
                  ApplicationViewModel(prodAppWithRespIndAndV2TermsOfUse, hasSubscriptionsFields = false, hasPpnsFields = false),
                  List.empty,
                  None,
                  termsOfUseViewModelV2
                ))

              page.termsOfUseAgreementV2.text shouldBe v2AgreementWording
              page.termsOfUseLinkV2 shouldBe null
            }
          }

          "the user is an administrator" should {
            val testUserName     = "Test User"
            val deadline         = instant.plus(java.time.Duration.ofDays(365))
            val expectedDeadline = DateFormatter.formatTwoDigitDay(deadline)
            val expectedDate     = DateFormatter.formatTwoDigitDay(instant)

            "have no link to read and agree when the terms of use have not been agreed" in new LoggedInUserIsAdmin {
              val checkInformation             = CheckInformation(termsOfUseAgreements = List.empty)
              val termsOfUseViewModelNotAgreed = termsOfUseViewModel.copy(agreement = None)

              val application = prodApp.withCheckInformation(checkInformation)

              val page =
                Page(applicationDetailsView(ApplicationViewModel(application, hasSubscriptionsFields = false, hasPpnsFields = false), List.empty, None, termsOfUseViewModelNotAgreed))

              page.termsOfUseAgreementV1 shouldBe null
            }

            "show agreement details, have a link to read and not show a warning when the terms of use have been agreed" in new LoggedInUserIsAdmin {
              val emailAddress      = "user@example.com".toLaxEmail
              val expectedTimeStamp = DateTimeFormatter.ofPattern("dd MMMM yyyy").withZone(ZoneOffset.UTC).format(instant)
              val version           = "1.0"
              val checkInformation  = CheckInformation(termsOfUseAgreements = List(TermsOfUseAgreement(emailAddress, instant, version)))

              val application = prodApp.withCheckInformation(checkInformation)

              val page =
                Page(applicationDetailsView(ApplicationViewModel(application, hasSubscriptionsFields = false, hasPpnsFields = false), List.empty, None, termsOfUseViewModel))

              page.termsOfUseAgreementV1.text shouldBe s"${emailAddress.text} agreed to version 1 of the terms of use on $expectedTimeStamp."
              page.termsOfUseLinkV1.text shouldBe "View"
              page.termsOfUseLinkV1.attributes.get("href") shouldBe routes.TermsOfUse.termsOfUse(application.id).url
              page.warning shouldBe null
            }

            "show 'Not agreed' and 'Start' link when V1 has no agreement and V2 is not started" in new LoggedInUserIsAdmin {
              val termsOfUseViewModelNotStarted = TermsOfUseViewModel(
                required = true,
                appUsesOldVersion = false,
                agreement = None,
                termsOfUseV2State = Some(TermsOfUseV2State.NotStarted())
              )

              val page =
                Page(applicationDetailsView(ApplicationViewModel(prodApp, hasSubscriptionsFields = false, hasPpnsFields = false), List.empty, None, termsOfUseViewModelNotStarted))

              page.termsOfUseNotAgreed.text shouldBe "Not agreed"
              page.termsOfUseStartLink.text shouldBe "Start"
            }

            "show V1 agreement with 'View' link and V2 uplift 'Start' link when V1 is agreed and V2 is not started" in new LoggedInUserIsAdmin {
              val termsOfUseViewModelV1AgreedV2NotStarted = TermsOfUseViewModel(
                required = true,
                appUsesOldVersion = true,
                agreement = Some(Agreement(testUserName, instant)),
                termsOfUseV2State = Some(TermsOfUseV2State.NotStarted(Some(deadline)))
              )

              val checkInformation = CheckInformation(termsOfUseAgreements = List(TermsOfUseAgreement("user@example.com".toLaxEmail, instant, "1.0")))
              val application      = prodApp.withCheckInformation(checkInformation)

              val page =
                Page(applicationDetailsView(
                  ApplicationViewModel(application, hasSubscriptionsFields = false, hasPpnsFields = false),
                  List.empty,
                  None,
                  termsOfUseViewModelV1AgreedV2NotStarted
                ))

              page.termsOfUseV2UpliftDetails.text should include("Our terms of use have changed.")
              page.termsOfUseV2UpliftDetails.text should include(s"Agree to version 2 of the terms of use by $expectedDeadline.")
              page.termsOfUseV2StartLink.text shouldBe "Start"
              page.termsOfUseV2StartLink.attributes.get("href") shouldBe uk.gov.hmrc.apiplatform.modules.uplift.controllers.routes.UpliftJourneyController.agreeNewTermsOfUse(
                application.id
              ).url
              page.termsOfUseAgreementV1.text shouldBe s"$testUserName agreed to version 1 of the terms of use on $expectedDate."
              page.termsOfUseLinkV1.attributes.get("href") shouldBe routes.TermsOfUse.termsOfUse(application.id).url
              page.termsOfUseLinkV1.text shouldBe "View"
            }

            "show started details and 'Continue' link when V1 has no agreement and V2 is started" in new LoggedInUserIsAdmin {
              val termsOfUseViewModelStarted = TermsOfUseViewModel(
                required = true,
                appUsesOldVersion = false,
                agreement = None,
                termsOfUseV2State = Some(TermsOfUseV2State.Started(testUserName, deadline))
              )

              val page =
                Page(applicationDetailsView(ApplicationViewModel(prodApp, hasSubscriptionsFields = false, hasPpnsFields = false), List.empty, None, termsOfUseViewModelStarted))

              page.termsOfUseStartedDetails.text should include(s"Terms of use started by $testUserName.")
              page.termsOfUseStartedDetails.text should include(s"Complete the version 2 of the terms of use by $expectedDeadline.")
              page.termsOfUseContinueLink.text shouldBe "Continue"
              page.termsOfUseContinueLink.attributes.get("href") shouldBe uk.gov.hmrc.apiplatform.modules.uplift.controllers.routes.UpliftJourneyController.agreeNewTermsOfUse(
                prodApp.id
              ).url
            }

            "show submitted details and 'View' link when V1 has no agreement and V2 is submitted" in new LoggedInUserIsAdmin {
              val termsOfUseViewModelSubmitted = TermsOfUseViewModel(
                required = true,
                appUsesOldVersion = false,
                agreement = None,
                termsOfUseV2State = Some(TermsOfUseV2State.Submitted(testUserName, instant))
              )

              val page =
                Page(applicationDetailsView(ApplicationViewModel(prodApp, hasSubscriptionsFields = false, hasPpnsFields = false), List.empty, None, termsOfUseViewModelSubmitted))

              page.termsOfUseSubmittedDetails.text should include(s"Terms of use submitted by $testUserName on $expectedDate.")
              page.termsOfUseSubmittedDetails.text should include("Your submission is being checked.")
              page.termsOfUseSubmittedViewLink.text shouldBe "View"
              page.termsOfUseSubmittedViewLink.attributes.get("href") shouldBe uk.gov.hmrc.apiplatform.modules.uplift.controllers.routes.UpliftJourneyController.agreeNewTermsOfUse(
                prodApp.id
              ).url

            }

            "show V2 agreement details with 'View' link and RI 'Change' link when V1 has no agreement and V2 is approved" in new LoggedInUserIsAdmin {
              val termsOfUseViewModelV2Approved = TermsOfUseViewModel(
                required = true,
                appUsesOldVersion = false,
                agreement = Some(Agreement(v2Agreement.name.getOrElse(v2Agreement.emailAddress.toString()), v2Agreement.date)),
                termsOfUseV2State = Some(TermsOfUseV2State.Approved(v2Agreement.name.getOrElse(v2Agreement.emailAddress.toString()), v2Agreement.date))
              )

              val page =
                Page(applicationDetailsView(
                  ApplicationViewModel(prodAppWithRespIndAndV2TermsOfUse, hasSubscriptionsFields = false, hasPpnsFields = false),
                  List.empty,
                  None,
                  termsOfUseViewModelV2Approved
                ))

              page.termsOfUseAgreementV2.text shouldBe v2AgreementWording
              page.termsOfUseLinkV2.text shouldBe "View"
              page.termsOfUseLinkV2.attributes.get(
                "href"
              ) shouldBe uk.gov.hmrc.apiplatform.modules.submissions.controllers.routes.TermsOfUseResponsesController.termsOfUseResponsesPage(prodApp.id).url
              page.responsibleIndividualLink.text shouldBe "Change"
              page.responsibleIndividualLink.attributes.get("href") shouldBe routes.ManageResponsibleIndividualController.showResponsibleIndividualDetails(prodApp.id).url
            }

            "show V1 agreement with 'View' link and V2 started details with 'Continue' link when V1 is agreed and V2 is started" in new LoggedInUserIsAdmin {
              val termsOfUseViewModelV1AgreedV2Started = TermsOfUseViewModel(
                required = true,
                appUsesOldVersion = true,
                agreement = Some(Agreement(testUserName, instant)),
                termsOfUseV2State = Some(TermsOfUseV2State.Started(testUserName, deadline))
              )

              val page =
                Page(applicationDetailsView(
                  ApplicationViewModel(prodApp, hasSubscriptionsFields = false, hasPpnsFields = false),
                  List.empty,
                  None,
                  termsOfUseViewModelV1AgreedV2Started
                ))

              page.termsOfUseStartedDetails.text should include(s"Terms of use started by $testUserName.")
              page.termsOfUseStartedDetails.text should include(s"Complete the version 2 of the terms of use by $expectedDeadline.")
              page.termsOfUseContinueLink.text shouldBe "Continue"
              page.termsOfUseContinueLink.attributes.get("href") shouldBe uk.gov.hmrc.apiplatform.modules.uplift.controllers.routes.UpliftJourneyController.agreeNewTermsOfUse(
                prodApp.id
              ).url
              page.termsOfUseAgreementV1.text shouldBe s"$testUserName agreed to version 1 of the terms of use on $expectedDate."
              page.termsOfUseLinkV1.text shouldBe "View"
              page.termsOfUseLinkV1.attributes.get("href") shouldBe routes.TermsOfUse.termsOfUse(prodApp.id).url
            }

            "show V1 agreement with 'View' link and V2 submitted details with 'View' link when V1 is agreed and V2 is submitted" in new LoggedInUserIsAdmin {
              val termsOfUseViewModelV1AgreedV2Submitted = TermsOfUseViewModel(
                required = true,
                appUsesOldVersion = true,
                agreement = Some(Agreement(testUserName, instant)),
                termsOfUseV2State = Some(TermsOfUseV2State.Submitted(testUserName, instant))
              )

              val page =
                Page(applicationDetailsView(
                  ApplicationViewModel(prodApp, hasSubscriptionsFields = false, hasPpnsFields = false),
                  List.empty,
                  None,
                  termsOfUseViewModelV1AgreedV2Submitted
                ))

              page.termsOfUseSubmittedDetails.text should include(s"Terms of use submitted by $testUserName on $expectedDate.")
              page.termsOfUseSubmittedDetails.text should include("Your submission is being checked.")
              page.termsOfUseSubmittedViewLink.text shouldBe "View"
              page.termsOfUseSubmittedViewLink.attributes.get("href") shouldBe uk.gov.hmrc.apiplatform.modules.uplift.controllers.routes.UpliftJourneyController.agreeNewTermsOfUse(
                prodApp.id
              ).url
              page.termsOfUseAgreementV1.text shouldBe s"$testUserName agreed to version 1 of the terms of use on $expectedDate."
              page.termsOfUseLinkV1.text shouldBe "View"
              page.termsOfUseLinkV1.attributes.get("href") shouldBe routes.TermsOfUse.termsOfUse(prodApp.id).url
            }

            "show V2 agreement details with 'View' link, RI 'Change' link, and no V1 details when V1 was agreed and V2 is approved" in new LoggedInUserIsAdmin {
              val termsOfUseViewModelV1AgreedV2Approved = TermsOfUseViewModel(
                required = true,
                appUsesOldVersion = false,
                agreement = Some(Agreement(v2Agreement.name.getOrElse(v2Agreement.emailAddress.toString()), v2Agreement.date)),
                termsOfUseV2State = Some(TermsOfUseV2State.Approved(v2Agreement.name.getOrElse(v2Agreement.emailAddress.toString()), v2Agreement.date))
              )

              val page =
                Page(applicationDetailsView(
                  ApplicationViewModel(prodAppWithRespIndAndV2TermsOfUse, hasSubscriptionsFields = false, hasPpnsFields = false),
                  List.empty,
                  None,
                  termsOfUseViewModelV1AgreedV2Approved
                ))

              page.termsOfUseAgreementV2.text shouldBe v2AgreementWording
              page.termsOfUseLinkV2.text shouldBe "View"
              page.termsOfUseLinkV2.attributes.get(
                "href"
              ) shouldBe uk.gov.hmrc.apiplatform.modules.submissions.controllers.routes.TermsOfUseResponsesController.termsOfUseResponsesPage(
                prodAppWithRespIndAndV2TermsOfUse.id
              ).url
              page.termsOfUseAgreementV1 shouldBe null
              page.responsibleIndividualLink.text shouldBe "Change"
              page.responsibleIndividualLink.attributes.get("href") shouldBe routes.ManageResponsibleIndividualController.showResponsibleIndividualDetails(
                prodAppWithRespIndAndV2TermsOfUse.id
              ).url
            }
          }
        }
      }
    }

    "showing Responsible individual details" when {
      "managing a sandbox application" should {
        val termsOfUseViewModelForSandboxApp = termsOfUseViewModel.copy(required = false)

        "show nothing when a developer" in new LoggedInUserIsDev {
          val page =
            Page(applicationDetailsView(ApplicationViewModel(sandboxApp, hasSubscriptionsFields = false, hasPpnsFields = false), List.empty, None, termsOfUseViewModelForSandboxApp))

          page.responsibleIndividual shouldBe null
        }

        "show nothing when an admin" in new LoggedInUserIsAdmin {
          val page =
            Page(applicationDetailsView(ApplicationViewModel(sandboxApp, hasSubscriptionsFields = false, hasPpnsFields = false), List.empty, None, termsOfUseViewModelForSandboxApp))

          page.responsibleIndividual shouldBe null
        }
      }

      "managing a production application" should {
        val termsOfUseViewModelForSandboxApp = termsOfUseViewModel.copy(required = true, appUsesOldVersion = false)

        "show nothing when a developer" in new LoggedInUserIsDev {
          val page =
            Page(applicationDetailsView(
              ApplicationViewModel(prodAppWithRespIndAndV2TermsOfUse, hasSubscriptionsFields = false, hasPpnsFields = false),
              List.empty,
              None,
              termsOfUseViewModelForSandboxApp
            ))

          page.responsibleIndividual.text() shouldBe "Responsible individual"
          page.responsibleIndividualName.text() shouldBe responsibleIndividualOne.fullName.toString()
          page.responsibleIndividualLink.text() shouldBe "View"
          page.responsibleIndividualLink.attributes.get("href") shouldBe routes.ManageResponsibleIndividualController.showResponsibleIndividualDetails(
            prodAppWithRespIndAndV2TermsOfUse.id
          ).url
        }

        "show nothing when an admin" in new LoggedInUserIsAdmin {
          val page =
            Page(applicationDetailsView(
              ApplicationViewModel(prodAppWithRespIndAndV2TermsOfUse, hasSubscriptionsFields = false, hasPpnsFields = false),
              List.empty,
              None,
              termsOfUseViewModelForSandboxApp
            ))

          page.responsibleIndividual.text() shouldBe "Responsible individual"
          page.responsibleIndividualName.text() shouldBe responsibleIndividualOne.fullName.toString()
          page.responsibleIndividualLink.text() shouldBe "Change"
          page.responsibleIndividualLink.attributes.get("href") shouldBe routes.ManageResponsibleIndividualController.showResponsibleIndividualDetails(
            prodAppWithRespIndAndV2TermsOfUse.id
          ).url
        }
      }
    }

    "showing Change links for application details" when {

      "managing a sandbox application" should {
        val appWithNoDesc = sandboxApp.modify(_.copy(description = None))
        val appWithDesc   = sandboxApp.modify(_.copy(description = Some("Test description")))

        "show 'Enter application description' link for description when logged in as a developer and there is no description" in new LoggedInUserIsDev {
          val page = Page(applicationDetailsView(ApplicationViewModel(appWithNoDesc, hasSubscriptionsFields = false, hasPpnsFields = false), List.empty, None, termsOfUseViewModel))

          page.descriptionCell.text shouldBe "Enter application description"
          page.changeAppDescriptionLink shouldBe null
        }

        "show 'Enter application description' link for description when logged in as an admin and there is no description" in new LoggedInUserIsAdmin {
          val page = Page(applicationDetailsView(ApplicationViewModel(appWithNoDesc, hasSubscriptionsFields = false, hasPpnsFields = false), List.empty, None, termsOfUseViewModel))

          page.descriptionCell.text shouldBe "Enter application description"
          page.changeAppDescriptionLink shouldBe null
        }

        "show description with Change link for description when logged in as a developer and there is a description" in new LoggedInUserIsDev {
          val page = Page(applicationDetailsView(ApplicationViewModel(appWithDesc, hasSubscriptionsFields = false, hasPpnsFields = false), List.empty, None, termsOfUseViewModel))

          page.descriptionCell.text shouldBe "Test description"
          page.changeAppDescriptionLink.text shouldBe "Change"
          page.changeAppDescriptionLink.attr("href") shouldBe routes.ManageApplicationController.changeAppNameAndDesc(appWithDesc.id).url
        }

        "show description with Change link for description when logged in as an admin and there is a description" in new LoggedInUserIsAdmin {
          val page = Page(applicationDetailsView(ApplicationViewModel(appWithDesc, hasSubscriptionsFields = false, hasPpnsFields = false), List.empty, None, termsOfUseViewModel))

          page.descriptionCell.text shouldBe "Test description"
          page.changeAppDescriptionLink.text shouldBe "Change"
          page.changeAppDescriptionLink.attr("href") shouldBe routes.ManageApplicationController.changeAppNameAndDesc(appWithDesc.id).url
        }

        "show Change link for application name when logged in as a developer" in new LoggedInUserIsDev {
          val page = Page(applicationDetailsView(ApplicationViewModel(sandboxApp, hasSubscriptionsFields = false, hasPpnsFields = false), List.empty, None, termsOfUseViewModel))

          page.applicationNameChangeLink.text shouldBe "Change"
          page.applicationNameChangeLink.attr("href") shouldBe routes.ManageApplicationController.changeAppNameAndDesc(sandboxApp.id).url
          page.body.getElementById("applicationName").text shouldBe sandboxApp.details.name.toString
        }

        "show Change link for application name when logged in as an admin" in new LoggedInUserIsAdmin {
          val page = Page(applicationDetailsView(ApplicationViewModel(sandboxApp, hasSubscriptionsFields = false, hasPpnsFields = false), List.empty, None, termsOfUseViewModel))

          page.applicationNameChangeLink.text shouldBe "Change"
          page.applicationNameChangeLink.attr("href") shouldBe routes.ManageApplicationController.changeAppNameAndDesc(sandboxApp.id).url
          page.body.getElementById("applicationName").text shouldBe sandboxApp.details.name.toString
        }
      }

      "managing a production application" should {
        val appWithNoDesc = prodApp.modify(_.copy(description = None))
        val appWithDesc   = prodApp.modify(_.copy(description = Some("Test description")))

        "show 'None' and no Change link for description when logged in as a developer and there is no description" in new LoggedInUserIsDev {
          val page = Page(applicationDetailsView(ApplicationViewModel(appWithNoDesc, hasSubscriptionsFields = false, hasPpnsFields = false), List.empty, None, termsOfUseViewModel))

          page.descriptionCell.text shouldBe "None"
          page.changeAppDescriptionLink shouldBe null
        }

        "show 'None' and no Change link for description when logged in as an admin and there is no description" in new LoggedInUserIsAdmin {
          val page = Page(applicationDetailsView(ApplicationViewModel(appWithNoDesc, hasSubscriptionsFields = false, hasPpnsFields = false), List.empty, None, termsOfUseViewModel))

          page.descriptionCell.text shouldBe "None"
          page.changeAppDescriptionLink shouldBe null
        }

        "show description without Change link for description when logged in as an admin and there is a description" in new LoggedInUserIsAdmin {
          val page = Page(applicationDetailsView(ApplicationViewModel(appWithDesc, hasSubscriptionsFields = false, hasPpnsFields = false), List.empty, None, termsOfUseViewModel))

          page.descriptionCell.text shouldBe "Test description"
          page.changeAppDescriptionLink shouldBe null
        }

        "show description without Change link for description when logged in as a developer and there is a description" in new LoggedInUserIsDev {
          val page = Page(applicationDetailsView(ApplicationViewModel(appWithDesc, hasSubscriptionsFields = false, hasPpnsFields = false), List.empty, None, termsOfUseViewModel))

          page.descriptionCell.text shouldBe "Test description"
          page.changeAppDescriptionLink shouldBe null
        }

        "show Change link for application name when logged in as an admin" in new LoggedInUserIsAdmin {
          val page = Page(applicationDetailsView(ApplicationViewModel(prodApp, hasSubscriptionsFields = false, hasPpnsFields = false), List.empty, None, termsOfUseViewModel))

          page.applicationNameChangeLink.text shouldBe "Change"
          page.applicationNameChangeLink.attr("href") shouldBe uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.routes.Details.requestChangeOfAppName(prodApp.id).url
          page.body.getElementById("applicationName").text shouldBe prodApp.details.name.toString
        }

        "show no Change link for application name when logged in as a developer" in new LoggedInUserIsDev {
          val page = Page(applicationDetailsView(ApplicationViewModel(prodApp, hasSubscriptionsFields = false, hasPpnsFields = false), List.empty, None, termsOfUseViewModel))

          page.applicationNameChangeLink shouldBe null
          page.body.getElementById("applicationName").text shouldBe prodApp.details.name.toString
        }
      }

      "managing a non-standard (ROPC or Privileged) application" should {

        "show no Change link for application name when a non-standard sandbox app and logged in as an admin" in new LoggedInUserIsAdmin {
          val application = sandboxApp.withAccess(Access.Ropc())
          val page        = Page(applicationDetailsView(ApplicationViewModel(application, hasSubscriptionsFields = false, hasPpnsFields = false), List.empty, None, termsOfUseViewModel))

          page.applicationNameChangeLink shouldBe null
        }

        "show no Change link for application name when a non-standard production app and logged in as an admin" in new LoggedInUserIsAdmin {
          val application = prodApp.withAccess(Access.Privileged())
          val page        = Page(applicationDetailsView(ApplicationViewModel(application, hasSubscriptionsFields = false, hasPpnsFields = false), List.empty, None, termsOfUseViewModel))

          page.applicationNameChangeLink shouldBe null
        }
      }
    }

    "showing Changing these application details" when {

      "managing a sandbox application" should {

        "show nothing when logged in as a developer because you can change details" in new LoggedInUserIsDev {
          val page = Page(applicationDetailsView(ApplicationViewModel(sandboxApp, hasSubscriptionsFields = false, hasPpnsFields = false), List.empty, None, termsOfUseViewModel))

          page.changingAppDetailsAdminList shouldBe null
        }

        "show nothing when logged in as an admin because you can change details" in new LoggedInUserIsAdmin {
          val page = Page(applicationDetailsView(ApplicationViewModel(sandboxApp, hasSubscriptionsFields = false, hasPpnsFields = false), List.empty, None, termsOfUseViewModel))

          page.changingAppDetailsAdminList shouldBe null
        }
      }

      "managing a production application" when {

        "show Changing these details section containing admin email address when logged in as a developer" in new LoggedInUserIsDev with ApplicationSyntaxes {

          val page = Page(applicationDetailsView(ApplicationViewModel(prodApp, hasSubscriptionsFields = false, hasPpnsFields = false), List.empty, None, termsOfUseViewModel))

          page.changingAppDetailsAdminList should not be null
          page.changingAppDetailsAdminList.text should include(adminEmail.text)
        }

        "show nothing when you are allowed to change the details" in new LoggedInUserIsAdmin {
          val page = Page(applicationDetailsView(ApplicationViewModel(prodApp, hasSubscriptionsFields = false, hasPpnsFields = false), List.empty, None, termsOfUseViewModel))

          page.changingAppDetailsAdminList shouldBe null
        }
      }
    }
  }
}
