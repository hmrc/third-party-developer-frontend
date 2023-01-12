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

import org.jsoup.Jsoup
import play.api.test.FakeRequest
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.ViewHelpers._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithCSRFAddToken
import views.helper.CommonViewSpec
import views.html.ExpiredVerificationLinkView

class ExpiredVerificationLinkSpec extends CommonViewSpec with WithCSRFAddToken {
  "Expired verification link page" should {

    val expiredVerificationLinkView = app.injector.instanceOf[ExpiredVerificationLinkView]
    val request                     = FakeRequest().withCSRFToken

    "render" in {
      val page = expiredVerificationLinkView.render(request, messagesProvider, appConfig)
      page.contentType should include("text/html")

      val document = Jsoup.parse(page.body)
      elementExistsByText(document, "h1", "Your account verification link has expired") shouldBe true
      elementExistsByText(document, "p", "You will need to register for a new account on the Developer Hub.") shouldBe true
    }
  }
}
