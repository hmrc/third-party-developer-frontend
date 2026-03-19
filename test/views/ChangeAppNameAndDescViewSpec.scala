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
import org.jsoup.nodes.Document
import play.api.test.FakeRequest
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.apiplatform.modules.tpd.test.data.UserTestData
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.DeveloperSessionBuilder
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.ChangeAppNameAndDescForm
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.ApplicationViewModel
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.ViewHelpers.{elementExistsByText, elementExistsContainsText, textareaExistsWithText, elementIdentifiedByIdContainsText}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithCSRFAddToken
import views.helper.CommonViewSpec
import views.html.manageapplication.ChangeAppNameAndDescView

class ChangeAppNameAndDescViewSpec extends CommonViewSpec
    with WithCSRFAddToken
    with LocalUserIdTracker
    with DeveloperSessionBuilder
    with UserTestData
    with FixedClock
    with ApplicationWithCollaboratorsFixtures {

  val changeDetails = app.injector.instanceOf[ChangeAppNameAndDescView]
  val applicationId = standardApp.id
  val clientId      = standardApp.clientId

  "change application details page" should {

    def renderPage(application: ApplicationWithCollaborators) = {

      val loggedIn              = adminDeveloper.loggedIn
      val request               = FakeRequest().withCSRFToken

      val form = ChangeAppNameAndDescForm.form.fill(
        ChangeAppNameAndDescForm(application.details.name.value, application.details.description)
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

      elementExistsByText(document, "h1", "Enter your application details") shouldBe true

      withClue("App name label")(elementIdentifiedByIdContainsText(document, "applicationName-label", "Application name") shouldBe true)
      withClue("App description label")(elementExistsContainsText(document, "label", "Application description") shouldBe true)
      elementExistsByText(document, "button", "Save and continue") shouldBe true
    }

    "pre-populate existing data fields" in {
      val aDescription           = Some("a helpful description")
      val application            = standardApp.inSandbox().withState(appStateTesting).modify(_.copy(description = aDescription))
      val document               = Jsoup.parse(renderPage(application).body)

      withClue("App Name")(formGroupWithLabelIsPrepopulated(document, "Application name", standardApp.name.value) shouldBe true)
      withClue("Description")(textareaExistsWithText(document, "description", aDescription.get) shouldBe true)
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
      val document    = Jsoup.parse(renderPage(application).body)

      elementExistsByText(document, "label", "Application name") shouldBe false
    }
  }
}
