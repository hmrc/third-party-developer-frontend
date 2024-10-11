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
import views.html.DetailsView

import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.twirl.api.HtmlFormat.Appendable

import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models.Access
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.{CheckInformation, TermsOfUseAgreement}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.Environment
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.{LoggedInState, UserSession}
import uk.gov.hmrc.apiplatform.modules.tpd.test.data.UserTestData
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.DeveloperSessionBuilder
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.Details.{Agreement, TermsOfUseViewModel}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.routes
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.ApplicationViewModel
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{TestApplications, WithCSRFAddToken, _}

class DetailsViewSpec
    extends CommonViewSpec
    with TestApplications
    with CollaboratorTracker
    with LocalUserIdTracker
    with WithCSRFAddToken
    with DeveloperSessionBuilder
    with UserTestData {

  val detailsView = app.injector.instanceOf[DetailsView]

  case class Page(doc: Appendable) {
    lazy val body: Document                             = Jsoup.parse(doc.body)
    lazy val environmentName: Element                   = body.getElementById("environmentName")
    lazy val warning: Element                           = body.getElementById("terms-of-use-header")
    lazy val termsOfUse: Element                        = body.getElementById("termsOfUse")
    lazy val agreementDetails: Element                  = body.getElementById("termsOfUseAgreementDetails")
    lazy val readLink: Element                          = body.getElementById("termsOfUseReadLink")
    lazy val changePrivacyPolicyLocationLink: Element   = body.getElementById("changePrivacyPolicyLocation")
    lazy val changeTermsConditionsLocationLink: Element = body.getElementById("changeTermsAndConditionsLocation")
    lazy val changingAppDetailsAdminList: Element       = body.getElementById("changingAppDetailsAdminList")
  }

  val termsOfUseViewModel = TermsOfUseViewModel(true, true, Some(Agreement("user@example.com", instant)))
  val adminEmail          = "admin@example.com".toLaxEmail

  implicit val loggedIn: UserSession = JoeBloggs.loggedIn

  trait LoggedInUserIsAdmin {
    implicit val loggedIn: UserSession = adminDeveloper.loggedIn
  }

  trait LoggedInUserIsDev {
    implicit val loggedIn: UserSession = standardDeveloper.loggedIn
  }

  "Application details view" when {
    implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
    implicit val navSection: String                           = "details"

    "rendering Environment " when {
      "managing a principal application" should {
        val deployedTo  = Environment.PRODUCTION
        val application = anApplication(environment = deployedTo)

        "Show Production when environment is Production" in {
          when(appConfig.nameOfPrincipalEnvironment).thenReturn("Production")
          when(appConfig.nameOfSubordinateEnvironment).thenReturn("Sandbox")
          val page = Page(detailsView(ApplicationViewModel(application, hasSubscriptionsFields = false, hasPpnsFields = false), termsOfUseViewModel))
          page.environmentName.text shouldBe "Production"
        }
        "Show QA when environment is QA" in {
          when(appConfig.nameOfPrincipalEnvironment).thenReturn("QA")
          when(appConfig.nameOfSubordinateEnvironment).thenReturn("Development")
          val page = Page(detailsView(ApplicationViewModel(application, hasSubscriptionsFields = false, hasPpnsFields = false), termsOfUseViewModel))
          page.environmentName.text shouldBe "QA"
        }
      }

      "managing a subordinate application" should {
        val deployedTo  = Environment.SANDBOX
        val application = anApplication(environment = deployedTo)

        "Show Sandbox when environment is Sandbox" in {
          when(appConfig.nameOfPrincipalEnvironment).thenReturn("Production")
          when(appConfig.nameOfSubordinateEnvironment).thenReturn("Sandbox")
          val page = Page(detailsView(ApplicationViewModel(application, hasSubscriptionsFields = false, hasPpnsFields = false), termsOfUseViewModel))
          page.environmentName.text shouldBe "Sandbox"
        }
        "Show Development when environment is Development" in {
          when(appConfig.nameOfPrincipalEnvironment).thenReturn("QA")
          when(appConfig.nameOfSubordinateEnvironment).thenReturn("Development")
          val page = Page(detailsView(ApplicationViewModel(application, hasSubscriptionsFields = false, hasPpnsFields = false), termsOfUseViewModel))
          page.environmentName.text shouldBe "Development"
        }
      }
    }

    "showing Change links for Privacy Policy and Terms & Conditions locations" when {
      "managing a sandbox application" should {
        val deployedTo                       = Environment.SANDBOX
        val termsOfUseViewModelForSandboxApp = termsOfUseViewModel.copy(exists = false)

        "show nothing when a developer" in {
          val application = anApplication(environment = deployedTo)

          val page = Page(detailsView(ApplicationViewModel(application, hasSubscriptionsFields = false, hasPpnsFields = false), termsOfUseViewModelForSandboxApp))

          page.changePrivacyPolicyLocationLink shouldBe null
          page.changeTermsConditionsLocationLink shouldBe null
        }

        "show nothing when an admin" in new LoggedInUserIsAdmin {
          val application = anApplication(environment = deployedTo)

          val page = Page(detailsView(ApplicationViewModel(application, hasSubscriptionsFields = false, hasPpnsFields = false), termsOfUseViewModelForSandboxApp))

          page.changePrivacyPolicyLocationLink shouldBe null
          page.changeTermsConditionsLocationLink shouldBe null
        }
      }
      "managing a production application" should {
        val deployedTo = Environment.PRODUCTION

        "show nothing when a developer" in {
          val application = anApplication(environment = deployedTo)

          val page = Page(detailsView(ApplicationViewModel(application, hasSubscriptionsFields = false, hasPpnsFields = false), termsOfUseViewModel))

          page.changePrivacyPolicyLocationLink shouldBe null
          page.changeTermsConditionsLocationLink shouldBe null
        }

        "show Change links when an admin" in new LoggedInUserIsAdmin {
          val application = anApplication(environment = deployedTo)

          val page = Page(detailsView(ApplicationViewModel(application, hasSubscriptionsFields = false, hasPpnsFields = false), termsOfUseViewModel))

          page.changePrivacyPolicyLocationLink.text should startWith("Change")
          page.changeTermsConditionsLocationLink.text should startWith("Change")
        }
      }
    }

    "showing Terms of Use details" when {
      "managing a sandbox application" should {
        val deployedTo                       = Environment.SANDBOX
        val termsOfUseViewModelForSandboxApp = termsOfUseViewModel.copy(exists = false)

        "show nothing when a developer" in {
          val application = anApplication(environment = deployedTo)

          val page = Page(detailsView(ApplicationViewModel(application, hasSubscriptionsFields = false, hasPpnsFields = false), termsOfUseViewModelForSandboxApp))

          page.termsOfUse shouldBe null
        }

        "show nothing when an admin" in new LoggedInUserIsAdmin {
          val application = anApplication(environment = deployedTo)

          val page = Page(detailsView(ApplicationViewModel(application, hasSubscriptionsFields = false, hasPpnsFields = false), termsOfUseViewModelForSandboxApp))

          page.termsOfUse shouldBe null
        }
      }

      "managing a production application" when {
        val deployedTo = Environment.PRODUCTION

        "the app is a privileged app" should {
          val access                        = Access.Privileged()
          val termsOfUseViewModelForPrivApp = termsOfUseViewModel.copy(exists = false)

          "show nothing when a developer" in {

            val application = anApplication(environment = deployedTo, access = access)

            val page = Page(detailsView(ApplicationViewModel(application, hasSubscriptionsFields = false, hasPpnsFields = false), termsOfUseViewModelForPrivApp))

            page.termsOfUse shouldBe null
          }

          "show nothing when an admin" in new LoggedInUserIsAdmin {
            val application = anApplication(environment = deployedTo, access = access)

            val page = Page(detailsView(ApplicationViewModel(application, hasSubscriptionsFields = false, hasPpnsFields = false), termsOfUseViewModelForPrivApp))

            page.termsOfUse shouldBe null
          }
        }

        "the app is an ROPC app" should {
          val access                        = Access.Ropc()
          val termsOfUseViewModelForRopcApp = termsOfUseViewModel.copy(exists = false)

          "show nothing when a developer" in {
            val application = anApplication(environment = deployedTo, access = access)
            val page        = Page(detailsView(ApplicationViewModel(application, hasSubscriptionsFields = false, hasPpnsFields = false), termsOfUseViewModelForRopcApp))

            page.termsOfUse shouldBe null
          }

          "show nothing when an admin" in new LoggedInUserIsAdmin {
            val application = anApplication(environment = deployedTo, access = access)

            val page = Page(detailsView(ApplicationViewModel(application, hasSubscriptionsFields = false, hasPpnsFields = false), termsOfUseViewModelForRopcApp))

            page.termsOfUse shouldBe null
          }
        }

        "the app is a standard app" when {
          val access = Access.Standard()

          "the user is a developer" should {
            "show 'not agreed' and have no link to read and agree when the terms of use have not been agreed" in {
              val checkInformation             = CheckInformation(termsOfUseAgreements = List.empty)
              val termsOfUseViewModelNotAgreed = termsOfUseViewModel.copy(agreement = None)

              val application = anApplication(environment = deployedTo, access = access)
                .withCheckInformation(checkInformation)

              val page = Page(detailsView(ApplicationViewModel(application, hasSubscriptionsFields = false, hasPpnsFields = false), termsOfUseViewModelNotAgreed))

              page.agreementDetails.text shouldBe "Not agreed"
              page.readLink shouldBe null
            }

            "show agreement details and have no link to read when the terms of use have been agreed" in {
              val emailAddress      = "user@example.com".toLaxEmail
              val expectedTimeStamp = DateTimeFormatter.ofPattern("dd MMMM yyyy").withZone(ZoneOffset.UTC).format(instant)
              val version           = "1.0"
              val checkInformation  = CheckInformation(termsOfUseAgreements = List(TermsOfUseAgreement(emailAddress, instant, version)))

              val application = anApplication(environment = deployedTo, access = access)
                .withCheckInformation(checkInformation)

              val page = Page(detailsView(ApplicationViewModel(application, hasSubscriptionsFields = false, hasPpnsFields = false), termsOfUseViewModel))

              page.agreementDetails.text shouldBe s"Agreed by ${emailAddress.text} on $expectedTimeStamp"
              page.readLink shouldBe null
            }
          }

          "the user is an administrator" should {

            "show 'not agreed', have a button to read and agree and show a warning when the terms of use have not been agreed" in new LoggedInUserIsAdmin {
              val checkInformation             = CheckInformation(termsOfUseAgreements = List.empty)
              val termsOfUseViewModelNotAgreed = termsOfUseViewModel.copy(agreement = None)

              val application = anApplication(environment = deployedTo, access = access)
                .withCheckInformation(checkInformation)

              val page = Page(detailsView(ApplicationViewModel(application, hasSubscriptionsFields = false, hasPpnsFields = false), termsOfUseViewModelNotAgreed))

              page.agreementDetails.text shouldBe "Not agreed"
              page.readLink.text shouldBe "Read and agree"
              page.readLink.attributes.get("href") shouldBe routes.TermsOfUse.termsOfUse(application.id).url
              page.warning.text shouldBe "Warning You must agree to the terms of use on this application."
            }

            "show agreement details, have a link to read and not show a warning when the terms of use have been agreed" in new LoggedInUserIsAdmin {
              val emailAddress      = "user@example.com".toLaxEmail
              val expectedTimeStamp = DateTimeFormatter.ofPattern("dd MMMM yyyy").withZone(ZoneOffset.UTC).format(instant)
              val version           = "1.0"
              val checkInformation  = CheckInformation(termsOfUseAgreements = List(TermsOfUseAgreement(emailAddress, instant, version)))

              val application = anApplication(environment = deployedTo, access = access)
                .withCheckInformation(checkInformation)

              val page = Page(detailsView(ApplicationViewModel(application, hasSubscriptionsFields = false, hasPpnsFields = false), termsOfUseViewModel))

              page.agreementDetails.text shouldBe s"Agreed by ${emailAddress.text} on $expectedTimeStamp"
              page.readLink.text shouldBe "Read"
              page.readLink.attributes.get("href") shouldBe routes.TermsOfUse.termsOfUse(application.id).url
              page.warning shouldBe null
            }
          }
        }
      }
    }

    "showing Changing these application details" when {
      "managing a sandbox application" should {
        val deployedTo = Environment.SANDBOX

        "show nothing when logged in as a developer because you can change details" in {
          val application = anApplication(environment = deployedTo)

          val page = Page(detailsView(ApplicationViewModel(application, hasSubscriptionsFields = false, hasPpnsFields = false), termsOfUseViewModel))

          page.changingAppDetailsAdminList shouldBe null
        }

        "show nothing when logged in as an admin because you can change details" in new LoggedInUserIsAdmin {
          val application = anApplication(environment = deployedTo)

          val page = Page(detailsView(ApplicationViewModel(application, hasSubscriptionsFields = false, hasPpnsFields = false), termsOfUseViewModel))

          page.changingAppDetailsAdminList shouldBe null
        }
      }

      "managing a production application" when {
        val deployedTo = Environment.PRODUCTION

        "show Changing these details section containing admin email address when logged in as a developer" in new LoggedInUserIsDev with ApplicationSyntaxes {
          val application = anApplication(environment = deployedTo)

          val page = Page(detailsView(ApplicationViewModel(application, hasSubscriptionsFields = false, hasPpnsFields = false), termsOfUseViewModel))

          page.changingAppDetailsAdminList should not be null
          page.changingAppDetailsAdminList.text should include(adminEmail.text)
        }

        "show nothing when you are allowed to change the details" in new LoggedInUserIsAdmin {
          val application = anApplication(environment = deployedTo)

          val page = Page(detailsView(ApplicationViewModel(application, hasSubscriptionsFields = false, hasPpnsFields = false), termsOfUseViewModel))

          page.changingAppDetailsAdminList shouldBe null
        }
      }
    }
  }
}
