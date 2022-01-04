/*
 * Copyright 2022 HM Revenue & Customs
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

import controllers.{FormKeys, SelectApisFromSubscriptionsForm}
import domain.models.applications.ApplicationId
import domain.models.connectors.ApiType.REST_API
import domain.models.connectors.{CombinedApi, CombinedApiCategory}
import domain.models.developers.{DeveloperSession, LoggedInState}
import domain.models.flows.NewApplicationEmailPreferencesFlowV2
import org.jsoup.Jsoup
import org.jsoup.nodes.{Document, Element}
import play.api.data.{Form, FormError}
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.twirl.api.Html
import utils.WithCSRFAddToken
import views.helper.CommonViewSpec
import views.html.emailpreferences.SelectApisFromSubscriptionsView

import scala.collection.JavaConverters._

class SelectApisFromSubscriptionsViewSpec extends CommonViewSpec with WithCSRFAddToken {

  trait Setup {
    val developerSessionWithoutEmailPreferences: DeveloperSession = {
      utils.DeveloperSession("email@example.com", "First Name", "Last Name", None, loggedInState = LoggedInState.LOGGED_IN)
    }
    val form = mock[Form[SelectApisFromSubscriptionsForm]]
    val apis = Set("api1", "api2")
    val applicationId = ApplicationId.random
    val newApplicationEmailPreferencesFlow = NewApplicationEmailPreferencesFlowV2(
        developerSessionWithoutEmailPreferences.session.sessionId,
        developerSessionWithoutEmailPreferences.developer.emailPreferences,
        applicationId,
        Set.empty,
        Set.empty,
        Set.empty
      )
    implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withCSRFToken

    val viewUnderTest: SelectApisFromSubscriptionsView = app.injector.instanceOf[SelectApisFromSubscriptionsView]
  }

  private def validateCheckboxItemsAgainstApis(document: Document, apis: List[CombinedApi]) = {
    apis.foreach(api => {
      val checkbox = document.getElementById(api.serviceName)
      checkbox.attr("name") shouldBe "selectedApi[]"
      checkbox.`val`() shouldBe api.serviceName

      document.select(s"label[for=${api.serviceName}]").text shouldBe api.displayName

      withClue("Expected number of checkboxes differs from number of apis sent to view") {
        document.select("input[type=checkbox]").size shouldBe (apis.size) // Include ALL_APIS checkbox
      }
    })
  }

  def validateStaticElements(document: Document, apis: List[CombinedApi], applicationId: ApplicationId) {
    document.getElementById("pageHeading").text() should be("Do you want to receive emails about the APIs you have subscribed to?")
    document.getElementById("select-all-description").text() should be("Select all that apply.")

    // Check form is configured correctly
    val form = document.getElementById("emailPreferencesApisForm")
    form.attr("method") should be("POST")
    form.attr("action") should be(s"/developer/profile/email-preferences/apis-from-subscriptions?context=${applicationId.value}")

    // check checkboxes are displayed
    validateCheckboxItemsAgainstApis(document, apis)

    document.getElementById("applicationId").`val`() shouldBe applicationId.value

    // Check submit button is correct
    document.getElementById("submit").text should be("Continue")
  }

  "New Application Email Preferences Select Api view page" should {
    val missingAPIs = List(CombinedApi("api1", "Api One",   List(CombinedApiCategory("category1"), CombinedApiCategory("category2")), REST_API),
      CombinedApi("api2", "Api Two", List(CombinedApiCategory("category2"), CombinedApiCategory("category4")), REST_API),
      CombinedApi("api3", "Api Three", List(CombinedApiCategory("category3"), CombinedApiCategory("category2")), REST_API))

    "render the api selection page with APIs that are missing from user's email preferences" in new Setup {
      // Missing APIs = some, Selected APIs = none
      when(form.errors).thenReturn(Seq.empty)
      when(form.errors(any[String])).thenReturn(Seq.empty)

      val page: Html =
        viewUnderTest.render(
          form, missingAPIs, applicationId, Set.empty, messagesProvider.messages, developerSessionWithoutEmailPreferences, request, appConfig)
      
      val document: Document = Jsoup.parse(page.body)
      validateStaticElements(document, missingAPIs, applicationId)
      Option(document.getElementById("error-summary-display")).isDefined shouldBe false
      document.select("input[type=checkbox][checked]").asScala.toList shouldBe List.empty
    }

     "render the api selection Page with user selected apis passed to the view" in new Setup {
       when(form.errors).thenReturn(Seq.empty)
       when(form.errors(any[String])).thenReturn(Seq.empty)

       val selectedAPIs = Set("api1")

       val page: Html =
         viewUnderTest.render(
           form, missingAPIs, applicationId, selectedAPIs, messagesProvider.messages, developerSessionWithoutEmailPreferences, request, appConfig)
     
       val document: Document = Jsoup.parse(page.body)
       validateStaticElements(document, missingAPIs, applicationId)
       Option(document.getElementById("error-summary-display")).isDefined shouldBe false

       val selectedBoxes: Seq[Element] = document.select("input[type=checkbox][checked]").asScala.toList
       selectedBoxes.map(_.attr("value")) should contain allElementsOf selectedAPIs
     }

     "render the form errors on the page when they exist" in new Setup {
       when(form.errors).thenReturn(Seq(FormError.apply(FormKeys.selectedApisNonSelectedKey, "message")))
       when(form.errors(any[String])).thenReturn(Seq(FormError.apply(FormKeys.selectedApisNonSelectedKey, "message")))

       val page: Html =
         viewUnderTest.render(
           form,
           missingAPIs,
           applicationId,
           Set.empty,
           messagesProvider.messages,
           developerSessionWithoutEmailPreferences,
           request,
           appConfig)
     
       val document: Document = Jsoup.parse(page.body)
       validateStaticElements(document, missingAPIs, applicationId)

       Option(document.getElementById("error-summary-display")).isDefined shouldBe true
     }
  }
}
