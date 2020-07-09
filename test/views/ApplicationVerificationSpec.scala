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

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import play.api.i18n.Messages.Implicits._
import play.api.test.FakeRequest
import play.twirl.api.Html
import utils.ViewHelpers._
import utils.WithCSRFAddToken
import views.helper.CommonViewSpec
import views.html.ApplicationVerificationView

class ApplicationVerificationSpec extends CommonViewSpec with WithCSRFAddToken {


  val applicationVerification = app.injector.instanceOf[ApplicationVerificationView]
  "Application verification page" should {

    def renderPage(success: Boolean): Html = {
      val request = FakeRequest().withCSRFToken
      applicationVerification.render(success, request, messagesProvider, appConfig)
    }

    "show email verified message when email was verified" in {
      val document = Jsoup.parse(renderPage(success = true).body)
      elementExistsByText(document, "h1", "Email verified") shouldBe true
      elementExistsByText(document, "h1", "The link has expired") shouldBe false
    }

    "show link has expired message when link has expired" in {
      val document: Document = Jsoup.parse(renderPage(success = false).body)
      elementExistsByText(document, "h1", "Email verified") shouldBe false
      elementExistsByText(document, "h1", "The link has expired") shouldBe true
    }
  }
}
