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
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.{ApplicationWithCollaborators, ApplicationWithCollaboratorsFixtures, CheckInformation, TermsOfUseAgreement}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.Environment
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.UserSession
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.Details.{Agreement, TermsOfUseViewModel}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.routes
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.ApplicationViewModel
import uk.gov.hmrc.thirdpartydeveloperfrontend.testdata.CommonSessionFixtures
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithCSRFAddToken

class ApplicationDetailsViewSpec
    extends CommonViewSpec
    with WithCSRFAddToken
    with CommonSessionFixtures
    with ApplicationWithCollaboratorsFixtures
    with FixedClock {

  implicit class AppAugment(val app: ApplicationWithCollaborators) {
    final def withCheckInformation(checkInformation: CheckInformation): ApplicationWithCollaborators = app.modify(_.copy(checkInformation = Some(checkInformation)))
  }

  val applicationDetailsView = app.injector.instanceOf[ApplicationDetailsView]

  case class Page(doc: Appendable) {
    lazy val body: Document                             = Jsoup.parse(doc.body)
    lazy val environmentName: Element                   = body.getElementById("environment")
    lazy val warning: Element                           = body.getElementById("terms-of-use-header")
    lazy val termsOfUse: Element                        = body.getElementById("termsOfUse")
    lazy val agreementDetails: Element                  = body.getElementById("termsOfUseAgreementV1")
    lazy val termsOfUseLink: Element                    = body.getElementById("termsOfUseLinkV1")
    lazy val privacyPolicy: Element                     = body.getElementById("privacyPolicy")
    lazy val changePrivacyPolicyLocationLink: Element   = body.getElementById("changePrivacyPolicy")
    lazy val termsAndConditions: Element                = body.getElementById("termsAndConditions")
    lazy val changeTermsConditionsLocationLink: Element = body.getElementById("changeTermsAndConditions")
    lazy val changingAppDetailsAdminList: Element       = body.getElementById("changingAppDetailsAdminList")
    lazy val descriptionCell: Element                   = body.getElementById("description")
    lazy val changeAppDescriptionLink: Element          = body.getElementById("changeAppDetailsLink")
    lazy val applicationNameChangeLink: Element         = body.getElementById("changeAppNameLink")
  }

  val termsOfUseViewModel = TermsOfUseViewModel(true, true, Some(Agreement("user@example.com", instant)))
  val sandboxApp          = standardApp.inSandbox()
  val prodApp             = standardApp.withEnvironment(Environment.PRODUCTION)

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

    "showing Change links for Privacy Policy and Terms & Conditions locations" when {
      val notSet = "Not set"
      "managing a sandbox application" should {

        val termsOfUseViewModelForSandboxApp = termsOfUseViewModel.copy(exists = false)

        "show nothing when a developer" in new LoggedInUserIsDev {
          val page =
            Page(applicationDetailsView(ApplicationViewModel(sandboxApp, hasSubscriptionsFields = false, hasPpnsFields = false), List.empty, None, termsOfUseViewModelForSandboxApp))

          page.privacyPolicy.text shouldBe notSet
          page.changePrivacyPolicyLocationLink shouldBe null
          page.termsAndConditions.text shouldBe notSet
          page.changeTermsConditionsLocationLink shouldBe null
        }

        "show nothing when an admin" in new LoggedInUserIsAdmin {

          val page =
            Page(applicationDetailsView(ApplicationViewModel(sandboxApp, hasSubscriptionsFields = false, hasPpnsFields = false), List.empty, None, termsOfUseViewModelForSandboxApp))

          page.privacyPolicy.text shouldBe notSet
          page.changePrivacyPolicyLocationLink shouldBe null
          page.termsAndConditions.text shouldBe notSet
          page.changeTermsConditionsLocationLink shouldBe null
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

          page.changePrivacyPolicyLocationLink.text should startWith("Change")
          page.changeTermsConditionsLocationLink.text should startWith("Change")
        }
      }
    }

    "showing Terms of Use details" when {
      "managing a sandbox application" should {
        val termsOfUseViewModelForSandboxApp = termsOfUseViewModel.copy(exists = false)

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
          val termsOfUseViewModelForPrivApp = termsOfUseViewModel.copy(exists = false)
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
          val termsOfUseViewModelForRopcApp = termsOfUseViewModel.copy(exists = false)
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
            "show 'not agreed' and have no link to read and agree when the terms of use have not been agreed" in new LoggedInUserIsDev {
              val checkInformation             = CheckInformation(termsOfUseAgreements = List.empty)
              val application                  = prodApp.withCheckInformation(checkInformation)
              val termsOfUseViewModelNotAgreed = termsOfUseViewModel.copy(agreement = None)

              val page =
                Page(applicationDetailsView(ApplicationViewModel(application, hasSubscriptionsFields = false, hasPpnsFields = false), List.empty, None, termsOfUseViewModelNotAgreed))

              page.agreementDetails shouldBe null
              page.termsOfUseLink shouldBe null
            }

            "show agreement details and have no link to read when the terms of use have been agreed" in new LoggedInUserIsDev {
              val emailAddress      = "user@example.com".toLaxEmail
              val expectedTimeStamp = DateTimeFormatter.ofPattern("dd MMMM yyyy").withZone(ZoneOffset.UTC).format(instant)
              val version           = "1.0"
              val checkInformation  = CheckInformation(termsOfUseAgreements = List(TermsOfUseAgreement(emailAddress, instant, version)))
              val application       = prodApp.withCheckInformation(checkInformation)

              val page =
                Page(applicationDetailsView(ApplicationViewModel(application, hasSubscriptionsFields = false, hasPpnsFields = false), List.empty, None, termsOfUseViewModel))

              page.agreementDetails.text shouldBe s"Agreed by ${emailAddress.text} on $expectedTimeStamp"
              page.termsOfUseLink shouldBe null
            }
          }

          "the user is an administrator" should {

            "show 'not agreed' when the terms of use have not been agreed" in new LoggedInUserIsAdmin {
              val checkInformation             = CheckInformation(termsOfUseAgreements = List.empty)
              val termsOfUseViewModelNotAgreed = termsOfUseViewModel.copy(agreement = None)

              val application = prodApp.withCheckInformation(checkInformation)

              val page =
                Page(applicationDetailsView(ApplicationViewModel(application, hasSubscriptionsFields = false, hasPpnsFields = false), List.empty, None, termsOfUseViewModelNotAgreed))

              page.agreementDetails shouldBe null
            }

            "show agreement details, have a link to read and not show a warning when the terms of use have been agreed" in new LoggedInUserIsAdmin {
              val emailAddress      = "user@example.com".toLaxEmail
              val expectedTimeStamp = DateTimeFormatter.ofPattern("dd MMMM yyyy").withZone(ZoneOffset.UTC).format(instant)
              val version           = "1.0"
              val checkInformation  = CheckInformation(termsOfUseAgreements = List(TermsOfUseAgreement(emailAddress, instant, version)))

              val application = prodApp.withCheckInformation(checkInformation)

              val page =
                Page(applicationDetailsView(ApplicationViewModel(application, hasSubscriptionsFields = false, hasPpnsFields = false), List.empty, None, termsOfUseViewModel))

              page.agreementDetails.text shouldBe s"Agreed by ${emailAddress.text} on $expectedTimeStamp"
              page.termsOfUseLink.text shouldBe "View"
              page.termsOfUseLink.attributes.get("href") shouldBe routes.TermsOfUse.termsOfUse(application.id).url
              page.warning shouldBe null
            }
          }
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
          page.changeAppDescriptionLink.attr("href") shouldBe routes.Details.changeDetails(appWithDesc.id).url
        }

        "show description with Change link for description when logged in as an admin and there is a description" in new LoggedInUserIsAdmin {
          val page = Page(applicationDetailsView(ApplicationViewModel(appWithDesc, hasSubscriptionsFields = false, hasPpnsFields = false), List.empty, None, termsOfUseViewModel))

          page.descriptionCell.text shouldBe "Test description"
          page.changeAppDescriptionLink.text shouldBe "Change"
          page.changeAppDescriptionLink.attr("href") shouldBe routes.Details.changeDetails(appWithDesc.id).url
        }

        "show Change link for application name when logged in as a developer" in new LoggedInUserIsDev {
          val page = Page(applicationDetailsView(ApplicationViewModel(sandboxApp, hasSubscriptionsFields = false, hasPpnsFields = false), List.empty, None, termsOfUseViewModel))

          page.applicationNameChangeLink.text shouldBe "Change"
          page.applicationNameChangeLink.attr("href") shouldBe routes.Details.changeDetails(sandboxApp.id).url
          page.body.getElementById("applicationName").text shouldBe sandboxApp.details.name.toString
        }

        "show Change link for application name when logged in as an admin" in new LoggedInUserIsAdmin {
          val page = Page(applicationDetailsView(ApplicationViewModel(sandboxApp, hasSubscriptionsFields = false, hasPpnsFields = false), List.empty, None, termsOfUseViewModel))

          page.applicationNameChangeLink.text shouldBe "Change"
          page.applicationNameChangeLink.attr("href") shouldBe routes.Details.changeDetails(sandboxApp.id).url
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
