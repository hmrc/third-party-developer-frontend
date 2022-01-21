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

package views.emailpreferences

import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.LoggedInState
import org.jsoup.Jsoup
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{DeveloperSessionBuilder, WithCSRFAddToken}
import views.helper.CommonViewSpec
import views.html.emailpreferences.EmailPreferencesUnsubscribeAllView

class EmailPreferencesUnsubscribeAllViewSpec extends CommonViewSpec with WithCSRFAddToken {

  trait Setup {
    implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withCSRFToken
    val developerSession = DeveloperSessionBuilder("email@example.com", "First Name", "Last Name", None, loggedInState = LoggedInState.LOGGED_IN)

    val emailPreferencesUnsubscribeAllView = app.injector.instanceOf[EmailPreferencesUnsubscribeAllView]
  }

  "Email Preferences Unsubscribe All page" should {
    "correctly render page elements" in new Setup {
      val page = emailPreferencesUnsubscribeAllView.render(messagesProvider.messages, developerSession, request, appConfig)
      val document = Jsoup.parse(page.body)

      // Check page title
      document.getElementById("pageHeading").text() should be("Are you sure you want to unsubscribe from Developer Hub emails?")

      // Check form is configured correctly
      val form = document.getElementById("unsubscribeForm")
      form.attr("method") should be ("POST")
      form.attr("action") should be ("/developer/profile/email-preferences/unsubscribe")

      document.getElementById("info-heading").text() shouldBe "Having a Developer Hub account means you will receive mandatory emails about:"
      //check the bullet points
      val elements = document.select("ul#info > li")
      elements.get(0).text() shouldBe "important notices and service updates"
      elements.get(1).text() shouldBe "changes to any applications you have"
      elements.get(2).text() shouldBe "making your application accessible"

      // Ensure CSRF token exists
      document.select("input[type=hidden][name=csrfToken]").isEmpty should be (false)

      // Check submit button is correct
      document.getElementById("submit").text should be ("Unsubscribe")

      // Check cancel link is correct
      val cancelLink = document.getElementById("cancelLink")
      cancelLink.text should be ("Cancel")
      cancelLink.attr("href") should be ("/developer/profile/email-preferences")
    }
  }
}
