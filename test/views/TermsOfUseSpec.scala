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
import controllers.TermsOfUseForm
import domain._
import org.joda.time.format.DateTimeFormat
import org.jsoup.Jsoup
import org.scalatest.Matchers
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.OneServerPerSuite
import play.api.i18n.Messages.Implicits.applicationMessages
import play.api.test.FakeRequest
import play.twirl.api.HtmlFormat.Appendable
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.time.DateTimeUtils
import utils.CSRFTokenHelper._
import utils.SharedMetricsClearDown

class TermsOfUseSpec extends UnitSpec with Matchers with MockitoSugar with OneServerPerSuite with SharedMetricsClearDown {
  case class Page(doc: Appendable) {
    lazy val body = Jsoup.parse(doc.body)
    lazy val title = body.title
    lazy val header = body.getElementsByTag("header").last
    lazy val alert = body.getElementById("termsOfUseAlert")
    lazy val termsOfUse = body.getElementById("termsOfUse")
    lazy val agreementForm = body.getElementById("termsOfUseForm")
  }

  "Terms of use view" when {
    implicit val mockConfig = mock[ApplicationConfig]
    implicit val request = FakeRequest().withCSRFToken
    implicit val loggedIn = utils.DeveloperSession("developer@example.com", "Joe", "Bloggs", loggedInState = LoggedInState.LOGGED_IN)
    implicit val navSection = "details"

    val id = "id"
    val clientId = "clientId"
    val appName = "an application"
    val createdOn = DateTimeUtils.now
    val lastAccess = DateTimeUtils.now
    val deployedTo = Environment.PRODUCTION

    "viewing an agreed application" should {
      trait Setup {
        val emailAddress = "email@example.com"
        val timeStamp = DateTimeUtils.now
        val expectedTimeStamp = DateTimeFormat.forPattern("dd MMMM yyyy").print(timeStamp)
        val version = "1.0"
        val checkInformation = CheckInformation(termsOfUseAgreements = Seq(TermsOfUseAgreement(emailAddress, timeStamp, version)))
        val application = Application(id, clientId, appName, createdOn, lastAccess, deployedTo, checkInformation = Some(checkInformation))
        val page = Page(views.html.termsOfUse(application, TermsOfUseForm.form))
      }

      "set the title and header to 'Terms of use'" in new Setup {
        page.title should startWith ("Terms of use")
        page.header.text shouldBe "Terms of use"
      }

      "show a notice stating when the terms of use were agreed to and by whom" in new Setup {
        page.alert.text shouldBe s"Terms of use accepted on $expectedTimeStamp by $emailAddress."
      }

      "render the terms of use" in new Setup  {
        page.termsOfUse should not be null
      }

      "not show the agreement form" in new Setup {
        page.agreementForm shouldBe null
      }
    }

    "viewing an unagreed application" should {
      trait Setup {
        val checkInformation = CheckInformation(termsOfUseAgreements = Seq.empty)
        val application = Application(id, clientId, appName, createdOn, lastAccess, deployedTo, checkInformation = Some(checkInformation))
        val page = Page(views.html.termsOfUse(application, TermsOfUseForm.form))
      }

      "set the title and header to 'Terms of use'" in new Setup {
        page.title should startWith ("Agree to our terms of use")
        page.header.text shouldBe "Agree to our terms of use"
      }

      "not show a notice stating when the terms of use were agreed to and by whom" in new Setup {
        page.alert shouldBe null
      }

      "render the terms of use" in new Setup {
        page.termsOfUse should not be null
      }

      "show the agreement form" in new Setup {
        page.agreementForm should not be null
      }
    }
  }
}
