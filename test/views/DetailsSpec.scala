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

import config.ApplicationConfig
import controllers.routes
import domain._
import model.ApplicationViewModel
import org.joda.time.format.DateTimeFormat
import org.jsoup.Jsoup
import org.jsoup.nodes.{Document, Element}
import org.scalatest.Matchers
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.OneServerPerSuite
import play.api.i18n.Messages.Implicits.applicationMessages
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.twirl.api.HtmlFormat.Appendable
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.time.DateTimeUtils
import utils.{SharedMetricsClearDown, TestApplications}

class DetailsSpec extends UnitSpec with Matchers with MockitoSugar with OneServerPerSuite with SharedMetricsClearDown with TestApplications {

  case class Page(doc: Appendable) {
    lazy val body: Document = Jsoup.parse(doc.body)
    lazy val warning: Element = body.getElementById("termsOfUseWarning")
    lazy val termsOfUse: Element = body.getElementById("termsOfUse")
    lazy val agreementDetails: Element = termsOfUse.getElementById("termsOfUseAagreementDetails")
    lazy val readLink: Element = termsOfUse.getElementById("termsOfUseReadLink")
  }

  "Application details view" when {
    implicit val mockConfig: ApplicationConfig = mock[ApplicationConfig]
    implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
    implicit val loggedIn: DeveloperSession = utils.DeveloperSession("developer@example.com", "Joe", "Bloggs", loggedInState = LoggedInState.LOGGED_IN)
    implicit val navSection: String = "details"

    "showing Terms of Use details" when {
      "managing a sandbox application" should {
        val deployedTo = Environment.SANDBOX

        "show nothing when a developer" in {
          val application = anApplication(environment = deployedTo)
            .withTeamMember(loggedIn.developer.email, Role.DEVELOPER)

          val page = Page(views.html.details(ApplicationViewModel(application, hasSubscriptions = false)))

          page.termsOfUse shouldBe null
        }

        "show nothing when an admin" in {
          val application = anApplication(environment = deployedTo)
            .withTeamMember(loggedIn.developer.email, Role.ADMINISTRATOR)

          val page = Page(views.html.details(ApplicationViewModel(application, hasSubscriptions = false)))

          page.termsOfUse shouldBe null
        }
      }

      "managing a production application" when {
        val deployedTo = Environment.PRODUCTION

        "the app is a privileged app" should {
          val access = Privileged()

          "show nothing when a developer" in {

            val application = anApplication(environment = deployedTo, access = access)
              .withTeamMember(loggedIn.developer.email, Role.DEVELOPER)

            val page = Page(views.html.details(ApplicationViewModel(application, hasSubscriptions = false)))

            page.termsOfUse shouldBe null
          }

          "show nothing when an admin" in {
            val application = anApplication(environment = deployedTo, access = access)
              .withTeamMember(loggedIn.developer.email, Role.ADMINISTRATOR)

            val page = Page(views.html.details(ApplicationViewModel(application, hasSubscriptions = false)))

            page.termsOfUse shouldBe null
          }
        }

        "the app is an ROPC app" should {
          val access = ROPC()

          "show nothing when a developer" in {
            val application = anApplication(environment = deployedTo, access = access)
              .withTeamMember(loggedIn.developer.email, Role.DEVELOPER)

            val page = Page(views.html.details(ApplicationViewModel(application, hasSubscriptions = false)))

            page.termsOfUse shouldBe null
          }

          "show nothing when an admin" in {
            val application = anApplication(environment = deployedTo, access = access)
              .withTeamMember(loggedIn.developer.email, Role.ADMINISTRATOR)

            val page = Page(views.html.details(ApplicationViewModel(application, hasSubscriptions = false)))

            page.termsOfUse shouldBe null
          }
        }

        "the app is a standard app" when {
          val access = Standard()

          "the user is a developer" should {
            "show 'not agreed' and have no link to read and agree when the terms of use have not been agreed" in {
              val checkInformation = CheckInformation(termsOfUseAgreements = Seq.empty)

              val application = anApplication(environment = deployedTo, access = access)
                .withTeamMember(loggedIn.developer.email, Role.ADMINISTRATOR)
                .withCheckInformation(checkInformation)

              val page = Page(views.html.details(ApplicationViewModel(application, hasSubscriptions = false)))

              page.agreementDetails.text shouldBe "Not agreed"
              page.readLink shouldBe null
            }

            "show agreement details and have no link to read when the terms of use have been agreed" in {
              val emailAddress = "email@example.com"
              val timeStamp = DateTimeUtils.now
              val expectedTimeStamp = DateTimeFormat.forPattern("dd MMMM yyyy").print(timeStamp)
              val version = "1.0"
              val checkInformation = CheckInformation(termsOfUseAgreements = Seq(TermsOfUseAgreement(emailAddress, timeStamp, version)))

              val application = anApplication(environment = deployedTo, access = access)
                .withTeamMember(loggedIn.developer.email, Role.ADMINISTRATOR)
                .withCheckInformation(checkInformation)

              val page = Page(views.html.details(ApplicationViewModel(application, hasSubscriptions = false)))

              page.agreementDetails.text shouldBe s"Agreed by $emailAddress on $expectedTimeStamp"
              page.readLink shouldBe null
            }
          }

          "the user is an administrator" should {

            val collaborators = Set(Collaborator(loggedIn.developer.email, Role.ADMINISTRATOR))

            "show 'not agreed', have a button to read and agree and show a warning when the terms of use have not been agreed" in {
              val checkInformation = CheckInformation(termsOfUseAgreements = Seq.empty)

              val application = anApplication(environment = deployedTo, access = access)
                .withTeamMembers(collaborators)
                .withCheckInformation(checkInformation)

              val page = Page(views.html.details(ApplicationViewModel(application, hasSubscriptions = false)))

              page.agreementDetails.text shouldBe "Not agreed"
              page.readLink.text shouldBe "Read and agree"
              page.readLink.attributes.get("href") shouldBe routes.TermsOfUse.termsOfUse(application.id).url
              page.warning.text shouldBe "Warning You must agree to the terms of use on this application."
            }

            "show agreement details, have a link to read and not show a warning when the terms of use have been agreed" in {
              val emailAddress = "email@example.com"
              val timeStamp = DateTimeUtils.now
              val expectedTimeStamp = DateTimeFormat.forPattern("dd MMMM yyyy").print(timeStamp)
              val version = "1.0"
              val checkInformation = CheckInformation(termsOfUseAgreements = Seq(TermsOfUseAgreement(emailAddress, timeStamp, version)))

              val application = anApplication(environment = deployedTo, access = access)
                .withTeamMembers(collaborators)
                .withCheckInformation(checkInformation)

              val page = Page(views.html.details(ApplicationViewModel(application, hasSubscriptions = false)))

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
