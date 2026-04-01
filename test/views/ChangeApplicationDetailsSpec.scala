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

import java.time.Period

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import views.helper.CommonViewSpec
import views.html.ChangeDetailsView

import play.api.test.FakeRequest

import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models._
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models._
import uk.gov.hmrc.apiplatform.modules.applications.submissions.domain.models.{PrivacyPolicyLocations, TermsAndConditionsLocations}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{ApplicationId, ClientId, Environment}
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.{LoggedInState, UserSession}
import uk.gov.hmrc.apiplatform.modules.tpd.test.data.UserTestData
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.DeveloperSessionBuilder
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.EditApplicationForm
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.ApplicationViewModel
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.ViewHelpers.{elementExistsByText, elementIdentifiedByAttrContainsText, textareaExistsWithText}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithCSRFAddToken

class ChangeApplicationDetailsSpec extends CommonViewSpec
    with WithCSRFAddToken
    with LocalUserIdTracker
    with DeveloperSessionBuilder
    with UserTestData
    with FixedClock
    with ApplicationWithCollaboratorsFixtures {

  val changeDetails = app.injector.instanceOf[ChangeDetailsView]
  val applicationId = standardApp.id
  val clientId      = standardApp.clientId

  "change application details page" should {

    def renderPage(application: ApplicationWithCollaborators) = {

      val loggedIn              = adminDeveloper.loggedIn
      val request               = FakeRequest().withCSRFToken
      val privacyPolicyUrl      = application.privacyPolicyLocation match {
        case Some(PrivacyPolicyLocations.Url(url)) => Some(url)
        case _                                     => None
      }
      val termsAndConditionsUrl = application.termsAndConditionsLocation match {
        case Some(TermsAndConditionsLocations.Url(url)) => Some(url)
        case _                                          => None
      }

      val form = EditApplicationForm.form.fill(
        EditApplicationForm(application.id, privacyPolicyUrl, termsAndConditionsUrl)
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

      val application = standardApp.inSandbox().withState(appStateTesting)
      val document    = Jsoup.parse(renderPage(application).body)

      elementExistsByText(document, "h1", "Change privacy policy and terms & conditions") shouldBe true

      elementIdentifiedByAttrContainsText(document, "div", "data-app-name", application.name.value) shouldBe true
      elementIdentifiedByAttrContainsText(document, "div", "data-app-env", "Sandbox") shouldBe true
      elementExistsByText(document, "button", "Save changes") shouldBe true
      elementExistsByText(document, "a", "Cancel") shouldBe true
      
      // Only privacy policy and T&C fields should exist
      document.getElementById("privacyPolicyUrl") should not be null
      document.getElementById("termsAndConditionsUrl") should not be null
      document.getElementById("applicationId") should not be null
      
      // Name and description fields should NOT exist (not even hidden) //todo remove after all tests pass
      document.select("#applicationName[type=text]").isEmpty shouldBe true
      document.select("textarea#description").isEmpty shouldBe true
      document.getElementById("grantLength") shouldBe null
    }

    "pre-populate existing data fields" in {
      val aDescription           = Some("a helpful description")
      val aPrivacyPolicyURL      = Some("a privacy policy url")
      val aTermsAndConditionsURL = Some("a terms and conditions url")
      val anAccess               = Access.Standard(privacyPolicyUrl = aPrivacyPolicyURL, termsAndConditionsUrl = aTermsAndConditionsURL)
      val application            = standardApp.inSandbox().withState(appStateTesting).withAccess(anAccess).modify(_.copy(description = aDescription))
      val document               = Jsoup.parse(renderPage(application).body)

      withClue("Privacy Policy URL")(formGroupWithLabelIsPrepopulated(document, "Privacy policy URL (optional)", aPrivacyPolicyURL.get) shouldBe true)
      withClue("T&C url")(formGroupWithLabelIsPrepopulated(document, "Terms and conditions URL (optional)", aTermsAndConditionsURL.get) shouldBe true)
      
      // App name and description should NOT be in the form
      document.select("label:contains(Application name)").isEmpty shouldBe true
      document.select("label:contains(Application description)").isEmpty shouldBe true
    }

    "form should only contain privacy policy and terms & conditions fields" in {
      val application = standardApp.inSandbox()
      val document    = Jsoup.parse(renderPage(application).body)

      document.getElementById("privacyPolicyUrl") should not be null
      document.getElementById("termsAndConditionsUrl") should not be null
      
      // Application name field should NOT exist (even for sandbox) //todo remove after pass
      elementExistsByText(document, "label", "Application name") shouldBe false
      
      // Description field should NOT exist  //todo remove after pass
      elementExistsByText(document, "label", "Application description") shouldBe false
      
      // Grant length hidden field should NOT exist  //todo remove after pass
      document.getElementById("grantLength") shouldBe null
    }
  }
}
