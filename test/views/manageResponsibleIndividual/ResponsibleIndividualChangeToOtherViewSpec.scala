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

package views.manageResponsibleIndividual

import org.jsoup.Jsoup
import views.helper.CommonViewSpec
import views.html.manageResponsibleIndividual.ResponsibleIndividualChangeToOtherView

import play.api.test.FakeRequest

import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.DeveloperSessionBuilder
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.{LoggedInState, UserSession}
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.ResponsibleIndividualChangeToOtherForm
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.ViewHelpers.{elementExistsByText, linkExistsWithHref}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{TestApplications, WithCSRFAddToken}
import uk.gov.hmrc.apiplatform.modules.tpd.test.data.DeveloperTestData

class ResponsibleIndividualChangeToOtherViewSpec extends CommonViewSpec with WithCSRFAddToken
    with LocalUserIdTracker with DeveloperSessionBuilder with TestApplications with DeveloperTestData {

  "Responsible Individual Change To Other View" should {
    val application = anApplication()
    val view        = app.injector.instanceOf[ResponsibleIndividualChangeToOtherView]
    val riName      = "Mr Responsible"
    val riEmail     = "ri@example.com"

    def renderPage() = {
      val request = FakeRequest().withCSRFToken
      val form    = ResponsibleIndividualChangeToOtherForm.form().fill(ResponsibleIndividualChangeToOtherForm(riName, riEmail))
      view.render(application, form, request, adminDeveloper.loggedIn, messagesProvider.messages, appConfig)
    }

    "display entered values correctly" in {
      val document = Jsoup.parse(renderPage().body)
      document.select(s"#name").attr("value") shouldBe riName
      document.select(s"#email").attr("value") shouldBe riEmail
    }

    "links to terms of use" in {
      val document = Jsoup.parse(renderPage().body)
      linkExistsWithHref(document, "/api-documentation/docs/terms-of-use") shouldBe true
      linkExistsWithHref(document, "/api-documentation/docs/terms-of-use/not-meeting-terms-of-use") shouldBe true
    }

    "contains paragraph explaining verification email" in {
      val document = Jsoup.parse(renderPage().body)
      elementExistsByText(document, "p", "We will email a verification link to the responsible individual which expires in 24 hours.")
    }
  }
}
