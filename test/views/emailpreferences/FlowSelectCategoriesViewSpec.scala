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

package views.emailpreferences

import views.helper.CommonViewSpec
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import domain.models.developers.LoggedInState
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import utils.WithCSRFAddToken
import views.html.emailpreferences.FlowSelectCategoriesView
import model.APICategoryDetails

class FlowSelectCategoriesViewSpec extends CommonViewSpec with WithCSRFAddToken {

  trait Setup {
    val developerSessionWithoutEmailPreferences =
      utils.DeveloperSession("email@example.com", "First Name", "Last Name", None, loggedInState = LoggedInState.LOGGED_IN)
    implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withCSRFToken

    val flowSelectCategoriesView = app.injector.instanceOf[FlowSelectCategoriesView]
  }

  def checkLink(document: Document, id: String, linkText: String, linkVal: String) = {
    withClue(s"Link text not as expected: for element: $id") {
      document.getElementById(id).text().startsWith(linkText) shouldBe true
    }
    document.getElementById(id).attr("href") shouldBe linkVal
  }

  "Email Preferences Select Categories view page" should {
    val categoriesFromAPM = List(APICategoryDetails("api1", "Api One"), APICategoryDetails("api2", "Api Two"))
    val usersCategories = Set("api1")

    "render the api categories selectuion Page, non selected " in new Setup {
      val page = flowSelectCategoriesView.render(categoriesFromAPM, Set.empty, messagesProvider.messages, developerSessionWithoutEmailPreferences, request, appConfig)
      val document = Jsoup.parse(page.body)

      document.getElementById("pageHeading").text() should be("Which API categories are you interested in?")

      // Check form is configured correctly
      val form = document.getElementById("emailPreferencesCategoriesForm")
      form.attr("method") should be ("POST")
      form.attr("action") should be ("/developer/profile/email-preferences/categories")

      // check checkboxes are displayed and unchecked
      categoriesFromAPM.foreach( category => {
          val checkbox = document.getElementById(category.category)
          checkbox.attr("name") shouldBe "taxRegime"
          checkbox.`val`() shouldBe category.category

          document.select(s"label[for=${category.category}]").text shouldBe category.name
      })

      document.select("input[type=checkbox]").size shouldBe categoriesFromAPM.size

      // Check submit button is correct
      document.getElementById("selectCategories").text should be ("Continue")
    }

  }
}
