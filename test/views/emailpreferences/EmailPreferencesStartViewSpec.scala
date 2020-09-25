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

package views.emailpreferences

import views.helper.CommonViewSpec
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import domain.models.developers.LoggedInState
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import utils.WithCSRFAddToken
import views.html.emailpreferences.EmailPreferencesStartView

class EmailPreferencesStartViewSpec extends CommonViewSpec with WithCSRFAddToken {

  trait Setup {
    val developerSessionWithoutEmailPreferences =
      utils.DeveloperSession("email@example.com", "First Name", "Last Name", None, loggedInState = LoggedInState.LOGGED_IN)
    implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withCSRFToken

    val emailPreferencesStartView = app.injector.instanceOf[EmailPreferencesStartView]
  }

  def checkLink(document: Document, id: String, linkText: String, linkVal: String) = {
    withClue(s"Link text not as expected: for element: $id") {
      document.getElementById(id).text().startsWith(linkText) shouldBe true
    }
    document.getElementById(id).attr("href") shouldBe linkVal
  }

  "Email Preferences Start view page" should {
    "render results table when email preferences have been selected" in new Setup {
      val page = emailPreferencesStartView.render(messagesProvider.messages, developerSessionWithoutEmailPreferences, request, appConfig)
      val document = Jsoup.parse(page.body)

      document.getElementById("pageHeading").text() should be("Email preferences")
      document.getElementById("firstSentence").text() should be("Manage your email preferences and choose the types of emails you want to receive from us.")

      val elements = document.select("ul#info > li")
      elements.get(0).text() shouldBe "important notices and service updates"
      elements.get(1).text() shouldBe "changes to any applications you have"
      elements.get(2).text() shouldBe "making your application accessible"

      // Check form is configured correctly
      val form = document.getElementById("emailPreferencesStartForm")
      form.attr("method") should be ("GET")
      // TODO: Confirm the action attribute of the form once next page is in place
//      form.attr("action") should be ("/developer/profile/email-preferences/...")

      // Check submit button is correct
      document.getElementById("submit").text should be ("Continue")
    }

  }
}
