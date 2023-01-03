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

package views.emailpreferences

import views.helper.CommonViewSpec
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.LoggedInState
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.{DeveloperBuilder, DeveloperSessionBuilder}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{LocalUserIdTracker, WithCSRFAddToken}
import views.html.emailpreferences.FlowStartView

class FlowStartViewSpec extends CommonViewSpec
  with WithCSRFAddToken
  with LocalUserIdTracker
  with DeveloperSessionBuilder
  with DeveloperBuilder {

  trait Setup {
    val developerSessionWithoutEmailPreferences =
      buildDeveloperSession( loggedInState = LoggedInState.LOGGED_IN, buildDeveloper("email@example.com", "First Name", "Last Name", None))
    implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withCSRFToken

    val flowStartView = app.injector.instanceOf[FlowStartView]
  }

  def checkLink(document: Document, id: String, linkText: String, linkVal: String) = {
    withClue(s"Link text not as expected: for element: $id") {
      document.getElementById(id).text().startsWith(linkText) shouldBe true
    }
    document.getElementById(id).attr("href") shouldBe linkVal
  }

  "Email Preferences Start view page" should {
    "render results table when email preferences have been selected" in new Setup {
      val page = flowStartView.render(messagesProvider.messages, developerSessionWithoutEmailPreferences, request, appConfig)
      val document = Jsoup.parse(page.body)

      document.getElementById("pageHeading").text() should be("Email preferences")
      document.getElementById("firstSentence").text() should be("Manage your email preferences and choose the types of emails you want to receive from us.")

      document.select("p#info-heading").text() should be ("Having a Developer Hub account means you will receive mandatory emails about:")
      document.select("p#info-footer").text() should be ("Emails from the Developer Hub and the Software Developer Support Team may include links and attachments.")

      val elements = document.select("ul#info > li")
      elements.get(0).text() shouldBe "important notices and service updates"
      elements.get(1).text() shouldBe "changes to any applications you have"
      elements.get(2).text() shouldBe "making your application accessible"

      // Check form is configured correctly
      val form = document.getElementById("emailPreferencesStartForm")
      form.attr("method") should be ("GET")

     form.attr("action") should be ("/developer/profile/email-preferences/categories")

      // Check submit button is correct
      document.getElementById("submit").text should be ("Continue")
    }

  }
}
