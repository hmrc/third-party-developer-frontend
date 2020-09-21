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

    val emailPreferences = EmailPreferences(List(TaxRegimeInterests("VAT", Set.empty), TaxRegimeInterests("INCOME_TAX_MTD", Set("income-tax-mtd-api-2", "income-tax-mtd-api-1"))), Set(EmailTopic.TECHNICAL, EmailTopic.BUSINESS_AND_POLICY))
    val developerSession = utils.DeveloperSession("email@example.com", "First Name", "Last Name", None, loggedInState = LoggedInState.LOGGED_IN, emailPreferences = emailPreferences)
    val developerSessionWithoutEmailPreferences = utils.DeveloperSession("email@example.com", "First Name", "Last Name", None, loggedInState = LoggedInState.LOGGED_IN)
    implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withCSRFToken

    val emailPreferencesSummaryView = app.injector.instanceOf[EmailPreferencesSummaryView]
  }


  def validateStaticElements(document: Document) = {
    println(document)

    document.getElementById("pageHeading").text() shouldNot be("Email Preferences")
    val elements = document.select("ul#info > li")
    elements.get(0).text() shouldBe "important notices and service updates"
    elements.get(1).text() shouldBe "changes to any applications you have"
    elements.get(2).text() shouldBe "making your application accessible"
    checkLink(document, "viewAllApplicationsLink", "View all applications", "/developer/applications")
    checkLink(document, "statusPageLink", "service availability page for information about live incidents", "https://api-platform-status.production.tax.service.gov.uk/")
    checkTableHeadings(document)

  }

  def checkLink(document: Document, id: String, linkText: String, linkVal: String) = {
    withClue(s"Link text not as expected: for element: $id"){document.getElementById(id).text().startsWith(linkText) shouldBe true}
    document.getElementById(id).attr("href") shouldBe linkVal
  }

  def checkTableHeadings(document: Document) {
    val tableHeaders = document.getElementsByTag("th")
    tableHeaders.get(0).text() shouldBe "Category"
    tableHeaders.get(1).text() shouldBe "APIs"
  }

  def checkAPITableRows(document: Document, emailPreferences: EmailPreferences, apiCategoryDetails: Seq[APICategoryDetails]): Unit = {
    for (interest <- emailPreferences.interests.zipWithIndex) {
      val textRegimeDisplayNameVal = taxRegimeDisplayName(apiCategoryDetails, interest._1.regime)
      document.getElementById(s"regime-col-${interest._2}").text() shouldBe textRegimeDisplayNameVal
      val services = interest._1.services
      val apisColText = if (services.isEmpty) s"All $textRegimeDisplayNameVal APIs" else services.mkString(" ")
      document.getElementById(s"apis-col-${interest._2}").text() shouldBe apisColText

      // Check Change link once wired up
    }
  }

  def checkTopicTableRow(document: Document, emailPreferences: EmailPreferences): Unit = {
      val topicsHeading = document.getElementById("topicsHeading")
      topicsHeading.text shouldBe "Topics"

      val selectedTopicsCell = document.getElementById("selectedTopicsCell")
      selectedTopicsCell.text shouldBe emailPreferences.topics.map(_.displayName).toList.sorted.mkString(" ")

      // Check Change link once wired up
  }

  def taxRegimeDisplayName(apiCategoryDetails: Seq[APICategoryDetails], taxRegime: String): String = {
    apiCategoryDetails.find(_.category == taxRegime).fold("Unknown")(_.name)
  }

  "Email Preferences Summary view page" should {
    "render results table when email preferences have been selected" in new Setup {
      val page = emailPreferencesSummaryView.render(apiCategoryDetails, messagesProvider.messages, developerSession, request, appConfig)
      val document = Jsoup.parse(page.body)
      validateStaticElements(document)
      checkAPITableRows(document, developerSession.developer.emailPreferences, apiCategoryDetails)
      checkTopicTableRow(document, developerSession.developer.emailPreferences)

      // Check unsubscribe link once wired up
    }

    // "display 'no email preferences selected' page for users that have not yet selected any" in new Setup {
    //     val page = emailPreferencesSummaryView.render(apiCategoryDetails, messagesProvider.messages, developerWithoutEmailPreferences, request, appConfig)
    //     val document = Jsoup.parse(page.body)
    //     validateStaticElements(document)
    // }
  }
}
