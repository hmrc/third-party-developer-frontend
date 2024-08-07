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

package views.noapplications

import org.jsoup.Jsoup
import views.helper.CommonViewSpec
import views.html.noapplications.NoApplicationsChoiceView

import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest

import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.{LoggedInState, UserSession}
import uk.gov.hmrc.apiplatform.modules.tpd.test.data.UserTestData
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.DeveloperSessionBuilder
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.noapplications.NoApplications.NoApplicationsChoiceForm
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.ViewHelpers._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithCSRFAddToken

class NoApplicationsChoiceViewSpec extends CommonViewSpec with WithCSRFAddToken with LocalUserIdTracker with DeveloperSessionBuilder with UserTestData {

  trait Setup {
    implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withCSRFToken

    val noApplicationsChoiceView: NoApplicationsChoiceView = app.injector.instanceOf[NoApplicationsChoiceView]

  }

  "No application page" should {

    "render with no errors" in new Setup {
      val page = noApplicationsChoiceView.render(NoApplicationsChoiceForm.form, request, JoeBloggs.loggedIn, messagesProvider, appConfig)

      page.contentType should include("text/html")

      val document = Jsoup.parse(page.body)

      elementExistsByText(document, "h1", "Using the Developer Hub") shouldBe true
      elementExistsById(document, "no-applications-hint") shouldBe true

      elementExistsById(document, "get-emails-item-hint") shouldBe true
      elementExistsContainsText(document, "label", "Get emails about our APIs") shouldBe true
      elementExistsById(document, "get-emails") shouldBe true
      elementExistsByIdWithAttr(document, "get-emails", "disabled") shouldBe false

      elementExistsById(document, "use-apis-item-hint") shouldBe true
      elementExistsContainsText(document, "label", "Start using our REST APIs") shouldBe true
      elementExistsById(document, "use-apis") shouldBe true
      elementExistsByIdWithAttr(document, "use-apis", "disabled") shouldBe false

    }

    "render with errors" in new Setup {
      val formWithErrors = NoApplicationsChoiceForm.form.withError("no.applications.choice.error.required.field", "Please select an option")
      val page           = noApplicationsChoiceView.render(formWithErrors, request, JoeBloggs.loggedIn, messagesProvider, appConfig)

      page.contentType should include("text/html")

      val document = Jsoup.parse(page.body)

      elementExistsByText(document, "h1", "Using the Developer Hub") shouldBe true
      elementExistsById(document, "no-applications-hint") shouldBe true

      elementExistsById(document, "get-emails-item-hint") shouldBe true
      elementExistsContainsText(document, "label", "Get emails about our APIs") shouldBe true
      elementExistsById(document, "get-emails") shouldBe true
      elementExistsByIdWithAttr(document, "get-emails", "disabled") shouldBe false

      elementExistsById(document, "use-apis-item-hint") shouldBe true
      elementExistsContainsText(document, "label", "Start using our REST APIs") shouldBe true
      elementExistsById(document, "use-apis") shouldBe true
      elementExistsByIdWithAttr(document, "use-apis", "disabled") shouldBe false

      elementExistsContainsText(document, "h2", "There is a problem") shouldBe true
      elementByAttributeValue(document, "href", "#no.applications.choice.error.required.field", "Please select an option") shouldBe true
    }
  }
}
