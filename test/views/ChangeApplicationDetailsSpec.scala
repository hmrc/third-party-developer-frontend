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

import java.time.{LocalDateTime, Period, ZoneOffset}
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import views.helper.CommonViewSpec
import views.html.ChangeDetailsView
import play.api.test.FakeRequest
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder._
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.EditApplicationForm
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.ApplicationViewModel
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.LoggedInState
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.ViewHelpers.{elementExistsByText, elementIdentifiedByAttrContainsText, textareaExistsWithText}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{LocalUserIdTracker, WithCSRFAddToken}
import uk.gov.hmrc.apiplatform.modules.applications.domain.models.{ApplicationId, ClientId, PrivacyPolicyLocations}

class ChangeApplicationDetailsSpec extends CommonViewSpec
    with WithCSRFAddToken
    with LocalUserIdTracker
    with DeveloperSessionBuilder
    with DeveloperTestData {

  val changeDetails = app.injector.instanceOf[ChangeDetailsView]
  val applicationId = ApplicationId.random
  val clientId      = ClientId("clientId123")

  "change application details page" should {

    def renderPage(application: Application) = {

      val loggedIn              = adminDeveloper.loggedIn
      val request               = FakeRequest().withCSRFToken
      val privacyPolicyUrl      = application.privacyPolicyLocation match {
        case PrivacyPolicyLocations.Url(url) => Some(url)
        case _                              => None
      }
      val termsAndConditionsUrl = application.termsAndConditionsLocation match {
        case TermsAndConditionsLocation.Url(url) => Some(url)
        case _                                   => None
      }

      val form = EditApplicationForm.form.fill(
        EditApplicationForm(application.id, application.name, application.description, privacyPolicyUrl, termsAndConditionsUrl, "12 months")
      )

      changeDetails.render(
        form,
        ApplicationViewModel(application, hasSubscriptionsFields = false, hasPpnsFields = false),
        request,
        loggedIn,
        messagesProvider,
        appConfig,
        "nav-section"
      )
    }

    def formGroupWithLabelIsPrepopulated(doc: Document, labelText: String, inputValue: String) = {

      val label              = doc.getElementsContainingText(labelText).last()
      val inputValueForLabel = label.parents().first().select("input").attr("value")

      inputValueForLabel == inputValue
    }

    "render" in {

      val application = Application(
        applicationId,
        clientId,
        "An App Name",
        LocalDateTime.now(ZoneOffset.UTC),
        Some(LocalDateTime.now(ZoneOffset.UTC)),
        None,
        Period.ofDays(547),
        Environment.SANDBOX
      )
      val document    = Jsoup.parse(renderPage(application).body)

      elementExistsByText(document, "h1", "Change application details") shouldBe true

      elementIdentifiedByAttrContainsText(document, "div", "data-app-name", application.name) shouldBe true
      elementIdentifiedByAttrContainsText(document, "div", "data-app-env", "Sandbox") shouldBe true
      elementExistsByText(document, "button", "Save changes") shouldBe true
      elementExistsByText(document, "a", "Cancel") shouldBe true
    }

    "pre-populate existing data fields" in {
      val aDescription           = Some("a helpful description")
      val aPrivacyPolicyURL      = Some("a privacy policy url")
      val aTermsAndConditionsURL = Some("a terms and conditions url")
      val standardAccess         = Standard(privacyPolicyUrl = aPrivacyPolicyURL, termsAndConditionsUrl = aTermsAndConditionsURL)
      val application            =
        Application(
          applicationId,
          clientId,
          "An App Name",
          LocalDateTime.now(ZoneOffset.UTC),
          Some(LocalDateTime.now(ZoneOffset.UTC)),
          None,
          grantLength,
          Environment.SANDBOX,
          description = aDescription,
          access = standardAccess
        )
      val document               = Jsoup.parse(renderPage(application).body)

      formGroupWithLabelIsPrepopulated(document, "Application name", "An App Name") shouldBe true
      textareaExistsWithText(document, "description", aDescription.get) shouldBe true
      formGroupWithLabelIsPrepopulated(document, "Privacy policy URL (optional)", aPrivacyPolicyURL.get) shouldBe true
      formGroupWithLabelIsPrepopulated(document, "Terms and conditions URL (optional)", aTermsAndConditionsURL.get) shouldBe true
    }

    "not display the option to change the app name if in prod pending GK approval" in {

      val application = Application(
        applicationId,
        clientId,
        "An App Name",
        LocalDateTime.now(ZoneOffset.UTC),
        Some(LocalDateTime.now(ZoneOffset.UTC)),
        None,
        grantLength,
        Environment.PRODUCTION,
        state = ApplicationState(State.PENDING_GATEKEEPER_APPROVAL, None, None)
      )
      val document    = Jsoup.parse(renderPage(application).body)

      elementExistsByText(document, "label", "Application name") shouldBe false
    }

    "not display the option to change the app name if in prod pending requestor verification" in {

      val application = Application(
        applicationId,
        clientId,
        "An App Name",
        LocalDateTime.now(ZoneOffset.UTC),
        Some(LocalDateTime.now(ZoneOffset.UTC)),
        None,
        grantLength,
        Environment.PRODUCTION,
        state = ApplicationState(State.PENDING_REQUESTER_VERIFICATION, None, None)
      )
      val document    = Jsoup.parse(renderPage(application).body)

      elementExistsByText(document, "label", "Application name") shouldBe false
    }

    "not display the option to change the app name if in prod with state production" in {

      val application =
        Application(
          applicationId,
          clientId,
          "An App Name",
          LocalDateTime.now(ZoneOffset.UTC),
          Some(LocalDateTime.now(ZoneOffset.UTC)),
          None,
          grantLength,
          Environment.PRODUCTION,
          state = ApplicationState(State.PRODUCTION, None, None)
        )
      val document    = Jsoup.parse(renderPage(application).body)

      elementExistsByText(document, "label", "Application name") shouldBe false
    }
  }
}
