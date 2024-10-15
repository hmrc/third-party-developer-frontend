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
        case _                               => None
      }
      val termsAndConditionsUrl = application.termsAndConditionsLocation match {
        case Some(TermsAndConditionsLocations.Url(url)) => Some(url)
        case _                                    => None
      }

      val form = EditApplicationForm.form.fill(
        EditApplicationForm(application.id, application.details.name.value, application.details.description, privacyPolicyUrl, termsAndConditionsUrl, "12 months")
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

      val application = standardApp.withEnvironment(Environment.SANDBOX).withState(appStateTesting)
      val document    = Jsoup.parse(renderPage(application).body)

      elementExistsByText(document, "h1", "Change application details") shouldBe true

      elementIdentifiedByAttrContainsText(document, "div", "data-app-name", application.name.value) shouldBe true
      elementIdentifiedByAttrContainsText(document, "div", "data-app-env", "Sandbox") shouldBe true
      elementExistsByText(document, "button", "Save changes") shouldBe true
      elementExistsByText(document, "a", "Cancel") shouldBe true
    }

    "pre-populate existing data fields" in {
      val aDescription           = Some("a helpful description")
      val aPrivacyPolicyURL      = Some("a privacy policy url")
      val aTermsAndConditionsURL = Some("a terms and conditions url")
      val anAccess               = Access.Standard(privacyPolicyUrl = aPrivacyPolicyURL, termsAndConditionsUrl = aTermsAndConditionsURL)
      val application            = standardApp.withEnvironment(Environment.SANDBOX).withState(appStateTesting).withAccess(anAccess).modify(_.copy(description = aDescription))
      val document               = Jsoup.parse(renderPage(application).body)

      withClue("App Name")(formGroupWithLabelIsPrepopulated(document, "Application name", standardApp.name.value) shouldBe true)
      withClue("Description")(textareaExistsWithText(document, "description", aDescription.get) shouldBe true)
      withClue("Privacy Policy URL")(formGroupWithLabelIsPrepopulated(document, "Privacy policy URL (optional)", aPrivacyPolicyURL.get) shouldBe true)
      withClue("T&C url")(formGroupWithLabelIsPrepopulated(document, "Terms and conditions URL (optional)", aTermsAndConditionsURL.get) shouldBe true)
    }

    "not display the option to change the app name if in prod pending GK approval" in {

      val application = standardApp.withState(appStatePendingGatekeeperApproval)
      val document    = Jsoup.parse(renderPage(application).body)

      elementExistsByText(document, "label", "Application name") shouldBe false
    }

    "not display the option to change the app name if in prod pending requestor verification" in {

      val application = standardApp.withState(appStatePendingRequesterVerification)
      val document    = Jsoup.parse(renderPage(application).body)

      elementExistsByText(document, "label", "Application name") shouldBe false
    }

    "not display the option to change the app name if in prod with state production" in {

      val application = standardApp
      // Application(
      //   applicationId,
      //   clientId,
      //   "An App Name",
      //   instant,
      //   Some(instant),
      //   None,
      //   grantLength,
      //   Environment.PRODUCTION,
      //   state = ApplicationState(State.PRODUCTION, None, None, None, instant)
      // )
      val document    = Jsoup.parse(renderPage(application).body)

      elementExistsByText(document, "label", "Application name") shouldBe false
    }
  }
}
