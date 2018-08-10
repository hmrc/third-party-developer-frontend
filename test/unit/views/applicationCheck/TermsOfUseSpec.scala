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

package unit.views.applicationCheck

import config.ApplicationConfig
import controllers.TermsOfUseForm
import domain._
import org.jsoup.Jsoup
import org.scalatestplus.play.{OneAppPerSuite, PlaySpec}
import play.api.test.FakeRequest
import uk.gov.hmrc.time.DateTimeUtils
import play.api.i18n.Messages.Implicits._
import utils.CSRFTokenHelper._

class TermsOfUseSpec extends PlaySpec with OneAppPerSuite {
  "Terms of use view" must {
    val thirdPartyAppplication =
      Application(
        "APPLICATION_ID",
        "CLIENT_ID",
        "APPLICATION NAME",
        DateTimeUtils.now,
        Environment.PRODUCTION,
        Some("APPLICATION DESCRIPTION"),
        Set(Collaborator("sample@example.com", Role.ADMINISTRATOR), Collaborator("someone@example.com", Role.DEVELOPER)),
        Standard(),
        true,
        ApplicationState(State.TESTING, None, None, DateTimeUtils.now)
      )

    "show terms of use agreement page that requires terms of use to be agreed" in {
      implicit val request = FakeRequest().withCSRFToken

      val checkInformation = CheckInformation(false, None, false, None, false, false, Seq.empty)
      val termsOfUseForm = TermsOfUseForm.fromCheckInformation(checkInformation)
      val developer = Developer("email@example.com", "First Name", "Last Name", None)

      val page = views.html.applicationcheck.termsOfUse.render(thirdPartyAppplication, TermsOfUseForm.form.fill(termsOfUseForm), request, developer, applicationMessages, ApplicationConfig, "credentials")
      page.contentType must include("text/html")

      val document = Jsoup.parse(page.body)
      document.getElementById("termsOfUseAgreed") mustNot be(null)
      document.getElementById("termsOfUseAgreed").attr("checked") mustNot be("checked")
    }

    "show terms of use agreement page that already has the correct terms of use agreed" in {
      implicit val request = FakeRequest().withCSRFToken

      val termsOfUseAgreement = TermsOfUseAgreement("email@example.com", DateTimeUtils.now, "1.0")
      val checkInformation = CheckInformation(false, None,false, None, false, false, Seq(termsOfUseAgreement))
      val termsOfUseForm = TermsOfUseForm.fromCheckInformation(checkInformation)
      val developer = Developer("email@example.com", "First Name", "Last Name", None)

      val page = views.html.applicationcheck.termsOfUse.render(thirdPartyAppplication.copy(checkInformation = Some(checkInformation)), TermsOfUseForm.form.fill(termsOfUseForm), request, developer, applicationMessages, ApplicationConfig, "credentials")
      page.contentType must include("text/html")

      val document = Jsoup.parse(page.body)
      page.body.contains("Terms of use agreed by email@example.com") mustBe true
    }
  }
}
