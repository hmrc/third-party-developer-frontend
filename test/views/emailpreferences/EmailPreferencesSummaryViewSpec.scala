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
import views.html.emailpreferences.EmailPreferencesSummaryView
import model.EmailPreferences
import model.TaxRegimeInterests
import model.EmailTopic
import model.APICategoryDetails

class EmailPreferencesSummaryViewSpec extends CommonViewSpec with WithCSRFAddToken {

  trait Setup {
    val apiCategoryDetails: Seq[APICategoryDetails] =
      Seq(APICategoryDetails("VAT", "VAT"), APICategoryDetails("INCOME_TAX_MTD", "Income Tax (Making Tax Digital)"))
    val apiCategoryDetailsMap = Map("VAT" -> "VAT", "INCOME_TAX_MTD" -> "Income Tax (Making Tax Digital)")

    val api1 =  "income-tax-mtd-api-1"
    val api2  = "income-tax-mtd-api-2"
    val extendedServiceDetails = Map(api1 -> "API1", api2 -> "API2")
    val emailPreferences = EmailPreferences(List(TaxRegimeInterests("VAT", Set.empty), TaxRegimeInterests("INCOME_TAX_MTD", Set(api1, api2))), Set(EmailTopic.TECHNICAL, EmailTopic.BUSINESS_AND_POLICY))
    val developerSession = utils.DeveloperSession("email@example.com", "First Name", "Last Name", None, loggedInState = LoggedInState.LOGGED_IN, emailPreferences = emailPreferences)
    val developerSessionWithoutEmailPreferences = utils.DeveloperSession("email@example.com", "First Name", "Last Name", None, loggedInState = LoggedInState.LOGGED_IN)
    implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withCSRFToken

    val emailPreferencesSummaryView = app.injector.instanceOf[EmailPreferencesSummaryView]
  }


  def validateStaticElements(document: Document) = {

    document.getElementById("pageHeading").text() shouldNot be("Email Preferences")
    val elements = document.select("ul#info > li")
    elements.get(0).text() shouldBe "important notices and service updates"
    elements.get(1).text() shouldBe "changes to any applications you have"
    elements.get(2).text() shouldBe "making your application accessible"
    checkLink(document, "viewAllApplicationsLink", "View all applications", "/developer/applications")
    checkLink(document, "statusPageLink", "service availability page for information about live incidents", "https://api-platform-status.production.tax.service.gov.uk/")
    

  }

  def checkLink(document: Document, id: String, linkText: String, linkVal: String) = {
    withClue(s"Link text not as expected: for element: $id"){document.getElementById(id).text().startsWith(linkText) shouldBe true}
    document.getElementById(id).attr("href") shouldBe linkVal
  }

  def checkEmailPreferencesTable(document: Document, emailPreferences: EmailPreferences, apiCategoryDetails: Seq[APICategoryDetails], extendedServiceDetails: Map[String, String]): Unit = {
    val tableHeaders = document.getElementsByTag("th")
    tableHeaders.get(0).text() shouldBe "Category"
    tableHeaders.get(1).text() shouldBe "APIs"

    for (interest <- emailPreferences.interests.sortBy(_.regime).zipWithIndex) {
      val textRegimeDisplayNameVal = taxRegimeDisplayName(apiCategoryDetails, interest._1.regime)
      document.getElementById(s"regime-col-${interest._2}").text() shouldBe textRegimeDisplayNameVal
      val services = interest._1.services
      val apisColText = if (services.isEmpty) s"All $textRegimeDisplayNameVal APIs" else services.map(extendedServiceDetails.get(_).getOrElse("")).mkString(" ")
      document.getElementById(s"apis-col-${interest._2}").text() shouldBe apisColText
      checkLink(document, s"change-${interest._2}-link", "Change", "/developer/profile/email-preferences")
    }

    val topicsHeading = document.getElementById("topicsHeading")
    topicsHeading.text shouldBe "Topics"

    val selectedTopicsCell = document.getElementById("selectedTopicsCell")
    selectedTopicsCell.text shouldBe emailPreferences.topics.map(_.displayName).toList.sorted.mkString(" ")
    checkLink(document, "changeTopicsLink", "Change", "/developer/profile/email-preferences")
  }

  def taxRegimeDisplayName(apiCategoryDetails: Seq[APICategoryDetails], taxRegime: String): String = {
    apiCategoryDetails.find(_.category == taxRegime).fold("Unknown")(_.name)
  }

  def checkNoEmailPreferencesPageElements(document: Document): Unit = {
    document.getElementById("firstLine").text shouldBe "You have selected no email preferences."
    checkLink(document, "setupEmailPreferencesLink", "Set up email preferences", "/developer/profile/email-preferences")
    document.select("a#unsubscribeLink").isEmpty shouldBe true
  }


  "Email Preferences Summary view page" should {
    "render results table when email preferences have been selected" in new Setup {

      val page = emailPreferencesSummaryView.render(EmailPreferencesSummaryViewData(apiCategoryDetailsMap, extendedServiceDetails), messagesProvider.messages, developerSession, request, appConfig)
      val document = Jsoup.parse(page.body)
      validateStaticElements(document)
      checkEmailPreferencesTable(document, developerSession.developer.emailPreferences, apiCategoryDetails, extendedServiceDetails)
    }

    "display 'no email preferences selected' page for users that have not yet selected any" in new Setup {
        val page = emailPreferencesSummaryView.render(EmailPreferencesSummaryViewData(apiCategoryDetailsMap, Map.empty), messagesProvider.messages, developerSessionWithoutEmailPreferences, request, appConfig)
        val document = Jsoup.parse(page.body)
        validateStaticElements(document)
        checkNoEmailPreferencesPageElements(document)
    }
  }
}
