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

import controllers.EditApplicationForm
import domain._
import model.ApplicationViewModel
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import play.api.test.FakeRequest
import uk.gov.hmrc.time.DateTimeUtils
import utils.ViewHelpers.{elementExistsByText, elementIdentifiedByAttrContainsText, textareaExistsWithText}
import utils.WithCSRFAddToken
import views.helper.CommonViewSpec
import views.html.ChangeDetailsView

class ChangeApplicationDetailsSpec extends CommonViewSpec with WithCSRFAddToken {

  val changeDetails = app.injector.instanceOf[ChangeDetailsView]

  "change application details page" should {

    def renderPage(application: Application) = {

      val loggedIn = utils.DeveloperSession("admin@example.com", "firstName1", "lastName1", loggedInState = LoggedInState.LOGGED_IN)
      val request = FakeRequest().withCSRFToken
      val form = EditApplicationForm.form.fill(EditApplicationForm(application.id, application.name, application.description,
        application.privacyPolicyUrl, application.termsAndConditionsUrl))

      changeDetails.render(
        form,
        ApplicationViewModel(application, hasSubscriptionsFields = false),
        request,
        loggedIn,
        messagesProvider,
        appConfig,
        "nav-section")
    }

    def formGroupWithLabelIsPrepopulated(doc: Document, labelText: String, inputValue: String) = {

      val label = doc.getElementsContainingText(labelText).last()
      val inputValueForLabel = label.parents().first().select("input").attr("value")

      inputValueForLabel == inputValue
    }

    "render" in {

      val application = Application("1234", "clientId1234", "An App Name", DateTimeUtils.now, DateTimeUtils.now, None, Environment.SANDBOX)
      val document = Jsoup.parse(renderPage(application).body)

      elementExistsByText(document, "h1", "Change application details") shouldBe true

      elementIdentifiedByAttrContainsText(document, "div", "data-app-name", application.name) shouldBe true
      elementIdentifiedByAttrContainsText(document, "div", "data-app-env", "Sandbox") shouldBe true
      elementExistsByText(document, "button", "Save changes") shouldBe true
      elementExistsByText(document, "a", "Cancel") shouldBe true
    }

    "pre-populate existing data fields" in {
      val aDescription = Some("a helpful description")
      val aPrivacyPolicyURL = Some("a privacy policy url")
      val aTermsAndConditionsURL = Some("a terms and conditions url")
      val standardAccess = Standard(privacyPolicyUrl = aPrivacyPolicyURL, termsAndConditionsUrl = aTermsAndConditionsURL)
      val application = Application("1234", "clientId1234", "An App Name", DateTimeUtils.now, DateTimeUtils.now, None, Environment.SANDBOX,
        description = aDescription,
        access = standardAccess)
      val document = Jsoup.parse(renderPage(application).body)

      formGroupWithLabelIsPrepopulated(document, "Application name", "An App Name") shouldBe true
      textareaExistsWithText(document, "description", aDescription.get) shouldBe true
      formGroupWithLabelIsPrepopulated(document, "Privacy policy URL (optional)", aPrivacyPolicyURL.get) shouldBe true
      formGroupWithLabelIsPrepopulated(document, "Terms and conditions URL (optional)", aTermsAndConditionsURL.get) shouldBe true
    }

    "not display the option to change the app name if in prod pending GK approval" in {

      val application = Application("1234", "clientId1234", "An App Name", DateTimeUtils.now, DateTimeUtils.now, None,
        Environment.PRODUCTION, state = ApplicationState(State.PENDING_GATEKEEPER_APPROVAL, None))
      val document = Jsoup.parse(renderPage(application).body)

      elementExistsByText(document, "label", "Application name") shouldBe false
    }

    "not display the option to change the app name if in prod pending requestor verification" in {

      val application = Application("1234", "clientId1234", "An App Name", DateTimeUtils.now, DateTimeUtils.now, None,
        Environment.PRODUCTION, state = ApplicationState(State.PENDING_REQUESTER_VERIFICATION, None))
      val document = Jsoup.parse(renderPage(application).body)

      elementExistsByText(document, "label", "Application name") shouldBe false
    }

    "not display the option to change the app name if in prod with state production" in {

      val application = Application("1234", "clientId1234", "An App Name", DateTimeUtils.now, DateTimeUtils.now, None,
        Environment.PRODUCTION, state = ApplicationState(State.PRODUCTION, None))
      val document = Jsoup.parse(renderPage(application).body)

      elementExistsByText(document, "label", "Application name") shouldBe false
    }
  }
}
