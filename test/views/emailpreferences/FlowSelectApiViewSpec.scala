/*
 * Copyright 2021 HM Revenue & Customs
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

import controllers.{FormKeys, SelectedApisEmailPreferencesForm}
import domain.models.connectors.ApiType.REST_API
import domain.models.connectors.{CombinedApi, CombinedApiCategory}
import domain.models.developers.{DeveloperSession, LoggedInState}
import domain.models.emailpreferences.APICategoryDisplayDetails
import domain.models.flows.EmailPreferencesFlowV2
import org.jsoup.Jsoup
import org.jsoup.nodes.{Document, Element}
import play.api.data.{Form, FormError}
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.twirl.api.Html
import utils.WithCSRFAddToken
import views.helper.CommonViewSpec
import views.html.emailpreferences.FlowSelectApiView

import scala.collection.JavaConverters._
import domain.models.connectors.ApiType

class FlowSelectApiViewSpec extends CommonViewSpec with WithCSRFAddToken {

  trait Setup {
    val developerSessionWithoutEmailPreferences: DeveloperSession = {
      utils.DeveloperSession("email@example.com", "First Name", "Last Name", None, loggedInState = LoggedInState.LOGGED_IN)
    }
    val form = mock[Form[SelectedApisEmailPreferencesForm]]
    val currentCategory = APICategoryDisplayDetails("CATEGORY1", "Category 1")
    val apis = Set("api1", "api2")
    val emailpreferencesFlow: EmailPreferencesFlowV2 = EmailPreferencesFlowV2(developerSessionWithoutEmailPreferences.session.sessionId, apis, Map(currentCategory.category -> apis), Set.empty, List.empty)
    implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withCSRFToken

    val flowSelectApiView: FlowSelectApiView = app.injector.instanceOf[FlowSelectApiView]
  }

  private def validateCheckboxItemsAgainstApis(document: Document, apis: List[CombinedApi], currentCategory: String): Unit = {
    val selectAllApiRadio = document.getElementById("all-apis")
    selectAllApiRadio.`val`() shouldBe "ALL_APIS"
    document.getElementById("all-apis-description").text() shouldBe s"You will be subscribed automatically to emails about new $currentCategory APIs"

    val SelectSpecificApiRadio = document.getElementById("individual-apis")
    SelectSpecificApiRadio.`val`() shouldBe "SOME_APIS"
    document.getElementById("individual-api-description").text() shouldBe s"Select specific APIs. You will not get emails about new Income Tax APIs"



    apis.foreach(api => {
      val checkbox = document.getElementById(api.serviceName)
      checkbox.attr("name") shouldBe "selectedApi[]"
      checkbox.`val`() shouldBe api.serviceName

      val expectedText =  if(api.apiType==ApiType.XML_API){s"${api.displayName} - XML API"} else s"${api.displayName}" 

      document.select(s"label[for=${api.serviceName}]").text shouldBe expectedText

      withClue("Expected number of checkboxes differs from number of apis sent to view") {
        document.select("input[type=checkbox]").size shouldBe apis.size // Include ALL_APIS checkbox
      }
    })
  }

  def validateStaticElements(document: Document, apis: List[CombinedApi], expectedCategory: APICategoryDisplayDetails): Unit = {

    document.getElementById("pageHeading").text() should be(s"Which ${expectedCategory.name} APIs are you interested in?")
    document.getElementById("select-all-description").text() should be("Select all that apply.")

    // Check form is configured correctly
    val form = document.getElementById("emailPreferencesApisForm")
    form.attr("method") should be("POST")
    form.attr("action") should be("/developer/profile/email-preferences/apis")

    // check checkboxes are displayed
    validateCheckboxItemsAgainstApis(document, apis, expectedCategory.name)

    //check category hidden field and value
    document.getElementById("current-category").`val`() shouldBe expectedCategory.category

    // Check submit button is correct
    document.getElementById("submit").text should be("Continue")
  }

  "Email Preferences Select Api view page" should {
    val apiList = List(CombinedApi("api1", "Api One", List(CombinedApiCategory("category1"), CombinedApiCategory("category1")), REST_API),
      CombinedApi("api2", "Api Two", List(CombinedApiCategory("category2"), CombinedApiCategory("category4")), REST_API),
      CombinedApi("api3", "Api Three", List(CombinedApiCategory("category3"), CombinedApiCategory("category2")), REST_API))
    val userApis = Set("api1", "api2")

    "render the api categories selection Page with no check boxes selected when no user selected categories passed into the view" in new Setup {
      when(form.errors).thenReturn(Seq.empty)
      when(form.errors(any[String])).thenReturn(Seq.empty)

      val page: Html = flowSelectApiView.render(form,  currentCategory, apiList, Set.empty, messagesProvider.messages, developerSessionWithoutEmailPreferences, request, appConfig)
      
      val document: Document = Jsoup.parse(page.body)
      validateStaticElements(document, apiList, currentCategory)
      Option(document.getElementById("error-summary-display")).isDefined shouldBe false
      document.select("input[type=checkbox][checked]").asScala.toList shouldBe List.empty
    }

    "render the api selection Page with boxes selected when user selected apis passed to the view" in new Setup {
      when(form.errors).thenReturn(Seq.empty)
      when(form.errors(any[String])).thenReturn(Seq.empty)

      val selectedApis = emailpreferencesFlow.selectedAPIs.getOrElse(currentCategory.category, Set.empty)
      
      val page: Html = flowSelectApiView.render(form,  currentCategory, apiList, selectedApis, messagesProvider.messages, developerSessionWithoutEmailPreferences, request, appConfig)
     
      val document: Document = Jsoup.parse(page.body)
      validateStaticElements(document, apiList, currentCategory)
      Option(document.getElementById("error-summary-display")).isDefined shouldBe false
      val selectedBoxes: Seq[Element] = document.select("input[type=checkbox][checked]").asScala.toList

      selectedBoxes.map(_.attr("value")) should contain allElementsOf userApis
    }

    "render the api selection Page  with All apis checked when ALL_APIS in flow for current category" in new Setup {
      when(form.errors).thenReturn(Seq.empty)
      when(form.errors(any[String])).thenReturn(Seq.empty)

      val selectedApis = userApis
      
      val page: Html = flowSelectApiView.render(form, currentCategory, apiList, selectedApis, messagesProvider.messages, developerSessionWithoutEmailPreferences, request, appConfig)
     
      val document: Document = Jsoup.parse(page.body)
      validateStaticElements(document, apiList, currentCategory)
      Option(document.getElementById("error-summary-display")).isDefined shouldBe false
      val selectedBoxes: Seq[Element] = document.select("input[type=checkbox][checked]").asScala.toList

      selectedBoxes.map(_.attr("value")) should contain allElementsOf selectedApis
    }

    "render the form errors on the page when they exist" in new Setup {
      when(form.errors).thenReturn(Seq(FormError.apply(FormKeys.selectedApisNonSelectedKey, "message")))
      when(form.errors(any[String])).thenReturn(Seq(FormError.apply(FormKeys.selectedApisNonSelectedKey, "message")))

      val selectedApis = emailpreferencesFlow.selectedAPIs.getOrElse(currentCategory.category, Set.empty)
      
      val page: Html = flowSelectApiView.render(form,  currentCategory, apiList, selectedApis, messagesProvider.messages, developerSessionWithoutEmailPreferences, request, appConfig)
     
      val document: Document = Jsoup.parse(page.body)
      validateStaticElements(document, apiList, currentCategory)
      Option(document.getElementById("error-summary-display")).isDefined shouldBe true
      val selectedBoxes: Seq[Element] = document.select("input[type=checkbox][checked]").asScala.toList

      selectedBoxes.map(_.attr("value")) should contain allElementsOf userApis
    }

  }
}
