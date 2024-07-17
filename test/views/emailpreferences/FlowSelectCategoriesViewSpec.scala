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

package views.emailpreferences

import scala.jdk.CollectionConverters._

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import views.helper.CommonViewSpec
import views.html.emailpreferences.FlowSelectCategoriesView

import play.api.data.Form
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.twirl.api.Html

import uk.gov.hmrc.apiplatform.modules.tpd.builder.DeveloperSessionBuilder
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.{LoggedInState, UserSession}
import uk.gov.hmrc.apiplatform.modules.tpd.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder._
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.TaxRegimeEmailPreferencesForm
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.emailpreferences.APICategoryDisplayDetails
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.flows.EmailPreferencesFlowV2
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.ViewHelpers._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithCSRFAddToken

class FlowSelectCategoriesViewSpec extends CommonViewSpec
    with WithCSRFAddToken
    with LocalUserIdTracker
    with DeveloperSessionBuilder
    with DeveloperTestData {

  trait Setup {
    val form = mock[Form[TaxRegimeEmailPreferencesForm]]

    val developerSessionWithoutEmailPreferences = standardDeveloper.loggedIn

    val emailPreferencesFlow                                  =
      EmailPreferencesFlowV2(developerSessionWithoutEmailPreferences.session.sessionId, Set("api1", "api2"), Map.empty, Set.empty, List.empty)
    implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withCSRFToken

    val flowSelectCategoriesView = app.injector.instanceOf[FlowSelectCategoriesView]
  }

  def checkLink(document: Document, id: String, linkText: String, linkVal: String) = {
    withClue(s"Link text not as expected: for element: $id") {
      document.getElementById(id).text().startsWith(linkText) shouldBe true
    }
    document.getElementById(id).attr("href") shouldBe linkVal
  }

  def validateCheckboxItemsAgainstCategories(document: Document, categories: List[APICategoryDisplayDetails]) = {
    categories.foreach(category => {
      val checkbox = document.getElementById(category.category)
      checkbox.attr("name") shouldBe "taxRegime[]"
      checkbox.`val`() shouldBe category.category

      document.select(s"label[for=${category.category}]").text shouldBe category.name

      withClue("Expected number of checkboxes differs from number of categories sent to view") {
        document.select("input[type=checkbox]").size shouldBe categories.size
      }
    })
  }

  def validateStaticElements(document: Document, categories: List[APICategoryDisplayDetails]): Unit = {

    document.getElementById("pageHeading").text() should be("Which API categories are you interested in?")
    // Check form is configured correctly
    val form = document.getElementById("emailPreferencesCategoriesForm")
    form.attr("method") should be("POST")
    form.attr("action") should be("/developer/profile/email-preferences/categories")

    // check checkboxes are displayed
    validateCheckboxItemsAgainstCategories(document, categories)

    // Check submit button is correct
    document.getElementById("selectCategories").text should be("Continue")
  }

  "Email Preferences Select Categories view page" should {
    val categoriesFromAPM = List(APICategoryDisplayDetails("api1", "Api One"), APICategoryDisplayDetails("api2", "Api Two"), APICategoryDisplayDetails("api3", "Api Three"))
    val usersCategories   = Set("api1", "api2")

    "render the api categories selection Page with no check boxes selected when no user selected categories passed into the view" in new Setup {
      override val request = FakeRequest().withCSRFToken

      val testForm = TaxRegimeEmailPreferencesForm.form

      val page: Html =
        flowSelectCategoriesView.render(
          testForm,
          categoriesFromAPM,
          Set.empty,
          messagesProvider.messages,
          developerSessionWithoutEmailPreferences,
          request,
          appConfig
        )

      val document = Jsoup.parse(page.body)
      validateStaticElements(document, categoriesFromAPM)
      elementIdentifiedByAttrWithValueContainsText(document, "h2", "id", "error-summary-title", "There is a problem") shouldBe false
      document.select("input[type=checkbox][checked]").asScala.toList shouldBe List.empty
    }

    "render the api categories selection Page with error summary displayed when form has errors" in new Setup {
      override val request = FakeRequest().withCSRFToken

      val formWithErrors = TaxRegimeEmailPreferencesForm.form.withError("key", "message")

      val page: Html =
        flowSelectCategoriesView.render(
          formWithErrors,
          categoriesFromAPM,
          Set.empty,
          messagesProvider.messages,
          developerSessionWithoutEmailPreferences,
          request,
          appConfig
        )

      page.contentType should include("text/html")

      val document = Jsoup.parse(page.body)

      validateStaticElements(document, categoriesFromAPM)
      elementIdentifiedByAttrWithValueContainsText(document, "h2", "id", "error-summary-title", "There is a problem") shouldBe true
      document.select("input[type=checkbox][checked]").asScala.toList shouldBe List.empty

    }

    "render the api categories selection Page with boxes selected when user selected categories passed to the view" in new Setup {
      override val request = FakeRequest().withCSRFToken

      val testForm = TaxRegimeEmailPreferencesForm.form

      val page: Html =
        flowSelectCategoriesView.render(
          testForm,
          categoriesFromAPM,
          emailPreferencesFlow.selectedCategories,
          messagesProvider.messages,
          developerSessionWithoutEmailPreferences,
          request,
          appConfig
        )

      val document      = Jsoup.parse(page.body)
      validateStaticElements(document, categoriesFromAPM)
      elementIdentifiedByAttrWithValueContainsText(document, "h2", "id", "error-summary-title", "There is a problem") shouldBe false
      val selectedBoxes = document.select("input[type=checkbox][checked]").asScala.toList

      selectedBoxes.map(_.attr("value")) should contain allElementsOf usersCategories
    }

  }
}
