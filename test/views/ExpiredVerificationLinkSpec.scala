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
import org.jsoup.Jsoup
import org.scalatest.Matchers
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.OneServerPerSuite
import play.api.i18n.Messages.Implicits._
import play.api.test.FakeRequest
import uk.gov.hmrc.play.test.UnitSpec
import utils.SharedMetricsClearDown
import utils.ViewHelpers._

class ExpiredVerificationLinkSpec extends UnitSpec with Matchers with MockitoSugar with OneServerPerSuite with SharedMetricsClearDown {
  "Expired verification link page" should {

    val appConfig = mock[ApplicationConfig]
    val request = FakeRequest().withCSRFToken

    "render" in {
      val page = views.html.expiredVerificationLink.render(request, applicationMessages, appConfig)
      page.contentType should include("text/html")

      val document = Jsoup.parse(page.body)
      elementExistsByText(document, "h1", "Your account verification link has expired") shouldBe true
      elementExistsByText(document, "p", "You will need to register for a new account on the Developer Hub.") shouldBe true
    }
  }
}
