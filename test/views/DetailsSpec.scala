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

import org.joda.time.DateTime
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.routes
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.{DeveloperSession, LoggedInState}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.ApplicationViewModel
import org.joda.time.format.DateTimeFormat
import org.jsoup.Jsoup
import org.jsoup.nodes.{Document, Element}
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.twirl.api.HtmlFormat.Appendable
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.Details.TermsOfUseViewModel
import uk.gov.hmrc.time.DateTimeUtils
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{TestApplications, WithCSRFAddToken}
import views.helper.CommonViewSpec
import views.html.DetailsView
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils._

class DetailsSpec 
    extends CommonViewSpec 
    with TestApplications 
    with CollaboratorTracker 
    with LocalUserIdTracker 
    with WithCSRFAddToken {

  val detailsView = app.injector.instanceOf[DetailsView]

  case class Page(doc: Appendable) {
    lazy val body: Document = Jsoup.parse(doc.body)
    lazy val environmentName: Element = body.getElementById("environmentName")
    lazy val warning: Element = body.getElementById("terms-of-use-header")
    lazy val termsOfUse: Element = body.getElementById("termsOfUse")
    lazy val agreementDetails: Element = body.getElementById("termsOfUseAgreementDetails")
    lazy val readLink: Element = body.getElementById("termsOfUseReadLink")
  }

  val termsOfUseViewModel = TermsOfUseViewModel(true, true, true, Some("user@example.com"), Some(DateTime.now))

  "Application details view" when {
    implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
    implicit val loggedIn: DeveloperSession = DeveloperSessionBuilder("developer@example.com", "Joe", "Bloggs", loggedInState = LoggedInState.LOGGED_IN)
    implicit val navSection: String = "details"

    "rendering Environment " when {
      "managing a principal application" should {
        val deployedTo = Environment.PRODUCTION
        val application = anApplication(environment = deployedTo)
          .withTeamMember(loggedIn.developer.email, CollaboratorRole.ADMINISTRATOR)

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
        val deployedTo = Environment.SANDBOX
        val application = anApplication(environment = deployedTo)
          .withTeamMember(loggedIn.developer.email, CollaboratorRole.ADMINISTRATOR)

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

    "showing Terms of Use details" when {
      "managing a sandbox application" should {
        val deployedTo = Environment.SANDBOX
        val termsOfUseViewModelForSandboxApp = termsOfUseViewModel.copy(exists = false)

        "show nothing when a developer" in {
          val application = anApplication(environment = deployedTo)
            .withTeamMember(loggedIn.developer.email, CollaboratorRole.DEVELOPER)

          val page = Page(detailsView(ApplicationViewModel(application, hasSubscriptionsFields = false, hasPpnsFields = false), termsOfUseViewModelForSandboxApp))

          page.termsOfUse shouldBe null
        }

        "show nothing when an admin" in {
          val application = anApplication(environment = deployedTo)
            .withTeamMember(loggedIn.developer.email, CollaboratorRole.ADMINISTRATOR)

          val page = Page(detailsView(ApplicationViewModel(application, hasSubscriptionsFields = false, hasPpnsFields = false), termsOfUseViewModelForSandboxApp))

          page.termsOfUse shouldBe null
        }
      }

      "managing a production application" when {
        val deployedTo = Environment.PRODUCTION

        "the app is a privileged app" should {
          val access = Privileged()
          val termsOfUseViewModelForPrivApp = termsOfUseViewModel.copy(exists = false)

          "show nothing when a developer" in {

            val application = anApplication(environment = deployedTo, access = access)
              .withTeamMember(loggedIn.developer.email, CollaboratorRole.DEVELOPER)

            val page = Page(detailsView(ApplicationViewModel(application, hasSubscriptionsFields = false, hasPpnsFields = false), termsOfUseViewModelForPrivApp))

            page.termsOfUse shouldBe null
          }

          "show nothing when an admin" in {
            val application = anApplication(environment = deployedTo, access = access)
              .withTeamMember(loggedIn.developer.email, CollaboratorRole.ADMINISTRATOR)

            val page = Page(detailsView(ApplicationViewModel(application, hasSubscriptionsFields = false, hasPpnsFields = false), termsOfUseViewModelForPrivApp))

            page.termsOfUse shouldBe null
          }
        }

        "the app is an ROPC app" should {
          val access = ROPC()
          val termsOfUseViewModelForRopcApp = termsOfUseViewModel.copy(exists = false)

          "show nothing when a developer" in {
            val application = anApplication(environment = deployedTo, access = access)
              .withTeamMember(loggedIn.developer.email, CollaboratorRole.DEVELOPER)

            val page = Page(detailsView(ApplicationViewModel(application, hasSubscriptionsFields = false, hasPpnsFields = false), termsOfUseViewModelForRopcApp))

            page.termsOfUse shouldBe null
          }

          "show nothing when an admin" in {
            val application = anApplication(environment = deployedTo, access = access)
              .withTeamMember(loggedIn.developer.email, CollaboratorRole.ADMINISTRATOR)

            val page = Page(detailsView(ApplicationViewModel(application, hasSubscriptionsFields = false, hasPpnsFields = false), termsOfUseViewModelForRopcApp))

            page.termsOfUse shouldBe null
          }
        }

        "the app is a standard app" when {
          val access = Standard()

          "the user is a developer" should {
            "show 'not agreed' and have no link to read and agree when the terms of use have not been agreed" in {
              val checkInformation = CheckInformation(termsOfUseAgreements = List.empty)
              val termsOfUseViewModelNotAgreed = termsOfUseViewModel.copy(agreed = false)

              val application = anApplication(environment = deployedTo, access = access)
                .withTeamMember(loggedIn.developer.email, CollaboratorRole.ADMINISTRATOR)
                .withCheckInformation(checkInformation)

              val page = Page(detailsView(ApplicationViewModel(application, hasSubscriptionsFields = false, hasPpnsFields = false), termsOfUseViewModelNotAgreed))

              page.agreementDetails.text shouldBe "Not agreed"
              page.readLink shouldBe null
            }

            "show agreement details and have no link to read when the terms of use have been agreed" in {
              val emailAddress = "user@example.com"
              val timeStamp = DateTimeUtils.now
              val expectedTimeStamp = DateTimeFormat.forPattern("dd MMMM yyyy").print(timeStamp)
              val version = "1.0"
              val checkInformation = CheckInformation(termsOfUseAgreements = List(TermsOfUseAgreement(emailAddress, timeStamp, version)))

              val application = anApplication(environment = deployedTo, access = access)
                .withTeamMember(loggedIn.developer.email, CollaboratorRole.ADMINISTRATOR)
                .withCheckInformation(checkInformation)

              val page = Page(detailsView(ApplicationViewModel(application, hasSubscriptionsFields = false, hasPpnsFields = false), termsOfUseViewModel))

              page.agreementDetails.text shouldBe s"Agreed by $emailAddress on $expectedTimeStamp"
              page.readLink shouldBe null
            }
          }

          "the user is an administrator" should {

            val collaborators = Set(loggedIn.developer.email.asAdministratorCollaborator)

            "show 'not agreed', have a button to read and agree and show a warning when the terms of use have not been agreed" in {
              val checkInformation = CheckInformation(termsOfUseAgreements = List.empty)
              val termsOfUseViewModelNotAgreed = termsOfUseViewModel.copy(agreed = false)

              val application = anApplication(environment = deployedTo, access = access)
                .withTeamMembers(collaborators)
                .withCheckInformation(checkInformation)

              val page = Page(detailsView(ApplicationViewModel(application, hasSubscriptionsFields = false, hasPpnsFields = false), termsOfUseViewModelNotAgreed))

              page.agreementDetails.text shouldBe "Not agreed"
              page.readLink.text shouldBe "Read and agree"
              page.readLink.attributes.get("href") shouldBe routes.TermsOfUse.termsOfUse(application.id).url
              page.warning.text shouldBe "Warning You must agree to the terms of use on this application."
            }

            "show agreement details, have a link to read and not show a warning when the terms of use have been agreed" in {
              val emailAddress = "user@example.com"
              val timeStamp = DateTimeUtils.now
              val expectedTimeStamp = DateTimeFormat.forPattern("dd MMMM yyyy").print(timeStamp)
              val version = "1.0"
              val checkInformation = CheckInformation(termsOfUseAgreements = List(applications.TermsOfUseAgreement(emailAddress, timeStamp, version)))

              val application = anApplication(environment = deployedTo, access = access)
                .withTeamMembers(collaborators)
                .withCheckInformation(checkInformation)

              val page = Page(detailsView(ApplicationViewModel(application, hasSubscriptionsFields = false, hasPpnsFields = false), termsOfUseViewModel))

              page.agreementDetails.text shouldBe s"Agreed by $emailAddress on $expectedTimeStamp"
              page.readLink.text shouldBe "Read"
              page.readLink.attributes.get("href") shouldBe routes.TermsOfUse.termsOfUse(application.id).url
              page.warning shouldBe null
            }
          }
        }
      }
    }
  }
}
