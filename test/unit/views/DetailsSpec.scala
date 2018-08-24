/*
 * Copyright 2018 HM Revenue & Customs
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

package unit.views

import org.joda.time.format.DateTimeFormat
import org.jsoup.Jsoup
import org.scalatest.Matchers
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.OneServerPerSuite
import play.api.i18n.Messages.Implicits.applicationMessages
import play.api.test.FakeRequest
import play.twirl.api.HtmlFormat.Appendable // ???
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.time.DateTimeUtils

import config.ApplicationConfig
import controllers.routes
import domain._

class DetailsSpec extends UnitSpec with Matchers with MockitoSugar with OneServerPerSuite {
  case class Page(doc: Appendable) {
    lazy val body = Jsoup.parse(doc.body)
    lazy val warning = body.getElementById("termsOfUseWarning")
    lazy val termsOfUse = body.getElementById("termsOfUse")
    lazy val agreementDetails = termsOfUse.getElementById("termsOfUseAagreementDetails")
    lazy val readLink = termsOfUse.getElementById("termsOfUseReadLink")
  }

  "Application details view" when {
    implicit val mockConfig = mock[ApplicationConfig]
    implicit val request = FakeRequest()
    implicit val loggedIn = Developer("developer@example.com", "Joe", "Bloggs")
    implicit val navSection = "details"

    val id = "id"
    val clientId = "clientId"
    val appName = "an application"
    val createdOn = DateTimeUtils.now

    "showing Terms of Use details" when {
      "managing a sandbox application" should {
        val deployedTo = Environment.SANDBOX

        "show nothing when a developer" in {
          val role = Role.DEVELOPER
          val application = Application(id, clientId, appName, createdOn, deployedTo)
          val page = Page(views.html.details(role, application))

          page.termsOfUse shouldBe null
        }

        "show nothing when an admin" in {
          val role = Role.ADMINISTRATOR
          val application = Application(id, clientId, appName, createdOn, deployedTo)
          val page = Page(views.html.details(role, application))

          page.termsOfUse shouldBe null
        }
      }

      "managing a production application" when {
        val deployedTo = Environment.PRODUCTION

        "the app is a privileged app" should {
          val access = Privileged()

          "show nothing when a developer" in {
            val role = Role.DEVELOPER
            val application = Application(id, clientId, appName, createdOn, deployedTo, access = access)
            val page = Page(views.html.details(role, application))

            page.termsOfUse shouldBe null
          }

          "show nothing when an admin" in {
            val role = Role.ADMINISTRATOR
            val application = Application(id, clientId, appName, createdOn, deployedTo, access = access)
            val page = Page(views.html.details(role, application))

            page.termsOfUse shouldBe null
          }
        }

        "the app is an ROPC app" should {
          val access = ROPC()

          "show nothing when a developer" in {
            val role = Role.DEVELOPER
            val application = Application(id, clientId, appName, createdOn, deployedTo, access = access)
            val page = Page(views.html.details(role, application))

            page.termsOfUse shouldBe null
          }

          "show nothing when an admin" in {
            val role = Role.ADMINISTRATOR
            val application = Application(id, clientId, appName, createdOn, deployedTo, access = access)
            val page = Page(views.html.details(role, application))

            page.termsOfUse shouldBe null
          }
        }

        "the app is a standard app" when {
          val access = Standard()

          "the user is a developer" should {
            val role = Role.DEVELOPER

            "show 'not agreed' and have no link to read and agree when the terms of use have not been agreed" in {
              val checkInformation = CheckInformation(termsOfUseAgreements = Seq.empty)
              val application = Application(id, clientId, appName, createdOn, deployedTo, access = access, checkInformation = Some(checkInformation))
              val page = Page(views.html.details(role, application))

              page.agreementDetails.text shouldBe "Not agreed"
              page.readLink shouldBe null
            }

            "show agreement details and have no link to read when the terms of use have been agreed" in {
              val emailAddress = "email@example.com"
              val timeStamp = DateTimeUtils.now
              val expectedTimeStamp = DateTimeFormat.forPattern("dd MMMM yyyy").print(timeStamp)
              val version = "1.0"
              val checkInformation = CheckInformation(termsOfUseAgreements = Seq(TermsOfUseAgreement(emailAddress, timeStamp, version)))
              val application = Application(id, clientId, appName, createdOn, deployedTo, access = access, checkInformation = Some(checkInformation))
              val page = Page(views.html.details(role, application))

              page.agreementDetails.text shouldBe s"Agreed by $emailAddress on $expectedTimeStamp"
              page.readLink shouldBe null
            }
          }

          "the user is an administrator" should {
            val role = Role.ADMINISTRATOR

            "show 'not agreed', have a button to read and agree and show a warning when the terms of use have not been agreed" in {
              val checkInformation = CheckInformation(termsOfUseAgreements = Seq.empty)
              val application = Application(id, clientId, appName, createdOn, deployedTo, access = access, checkInformation = Some(checkInformation))
              val page = Page(views.html.details(role, application))

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
              val application = Application(id, clientId, appName, createdOn, deployedTo, access = access, checkInformation = Some(checkInformation))
              val page = Page(views.html.details(role, application))

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
