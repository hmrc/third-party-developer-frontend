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

import views.helper.CommonViewSpec
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import domain.models.developers.LoggedInState
import domain.models.emailpreferences.{APICategoryDetails, EmailPreferences, EmailTopic, TaxRegimeInterests}
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import utils.WithCSRFAddToken
import views.html.emailpreferences.EmailPreferencesSummaryView

class EmailPreferencesSummaryViewSpec extends CommonViewSpec with WithCSRFAddToken {

  trait Setup {
    val apiCategoryDetails: Seq[APICategoryDetails] =
      Seq(APICategoryDetails("VAT", "VAT"), APICategoryDetails("INCOME_TAX_MTD", "Income Tax (Making Tax Digital)"))
    val apiCategoryDetailsMap = Map("VAT" -> "VAT", "INCOME_TAX_MTD" -> "Income Tax (Making Tax Digital)")

    val api1 = "income-tax-mtd-api-1"
    val api2 = "income-tax-mtd-api-2"
    val extendedServiceDetails = Map(api1 -> "API1", api2 -> "API2")
    val emailPreferences =
      EmailPreferences(
        List(TaxRegimeInterests("VAT", Set.empty), TaxRegimeInterests("INCOME_TAX_MTD", Set(api1, api2))),
        Set(EmailTopic.TECHNICAL, EmailTopic.BUSINESS_AND_POLICY))

    val developerSession =
      utils.DeveloperSession("email@example.com", "First Name", "Last Name", None, loggedInState = LoggedInState.LOGGED_IN, emailPreferences = emailPreferences)

    val developerSessionWithoutEmailPreferences =
      utils.DeveloperSession("email@example.com", "First Name", "Last Name", None, loggedInState = LoggedInState.LOGGED_IN)
    implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withCSRFToken

    val emailPreferencesSummaryView = app.injector.instanceOf[EmailPreferencesSummaryView]
  }


  def validateStaticElements(document: Document) = {

    document.getElementById("page-heading").text() shouldNot be("Email Preferences")

    checkLink(
      document,
      "status-page-link",
      "service availability page for information about live incidents",
      "https://api-platform-status.production.tax.service.gov.uk/")

    checkLink(document, "view-all-applications-link", "View all applications", "/developer/applications")
  }

  def checkLink(document: Document, id: String, linkText: String, linkVal: String) = {
    withClue(s"Link text not as expected: for element: $id") {
      document.getElementById(id).text().startsWith(linkText) shouldBe true
    }
    document.getElementById(id).attr("href") shouldBe linkVal
  }

  def checkEmailPreferencesApisSection(document: Document,
                                       emailPreferences: EmailPreferences,
                                       apiCategoryDetails: Seq[APICategoryDetails],
                                       extendedServiceDetails: Map[String, String]): Unit = {

    for (interest <- emailPreferences.interests.sortBy(_.regime)) {
      val textRegimeDisplayNameVal = taxRegimeDisplayName(apiCategoryDetails, interest.regime)
      val categoryHeading = document.getElementById(s"category-heading-${interest.regime}")
      categoryHeading.text shouldBe textRegimeDisplayNameVal

      val services = interest.services
      if (services.isEmpty) {
        document.getElementById(s"all-api-${interest.regime}").text() shouldBe s"All $textRegimeDisplayNameVal APIs"
      } else {
        for (service <- services) {
          document.getElementById(s"$service").text() shouldBe extendedServiceDetails.getOrElse(service, "")
        }
      }

      checkLink(document, "change-apis-link", "Edit your preferences or get emails about other APIs", "/developer/profile/email-preferences/categories")
    }
  }


  def checkEmailTopicsSection(document: Document, emailPreferences: EmailPreferences): Unit = {

    val topicsHeading = document.getElementById("topics-heading")
    topicsHeading.text shouldBe "Topics"
    for (topic <- emailPreferences.topics) {
      val selectedTopicsCell = document.getElementById(topic.value)
      selectedTopicsCell.text shouldBe topic.displayName
    }

    checkLink(document, "change-topics-link", "Change the topics you are interested in", "/developer/profile/email-preferences/topics")
    checkLink(document, "unsubscribe-link", "Unsubscribe from Developer Hub emails", "/developer/profile/email-preferences/unsubscribe")
  }

  def taxRegimeDisplayName(apiCategoryDetails: Seq[APICategoryDetails], taxRegime: String): String = {
    apiCategoryDetails.find(_.category == taxRegime).fold("Unknown")(_.name)
  }

  def checkNoEmailPreferencesPageElements(document: Document): Unit = {
    document.getElementById("first-line").text shouldBe "You have selected no email preferences."
    checkLink(document, "setup-emailpreferences-link", "Set up email preferences", "/developer/profile/email-preferences/start")
    document.select("a#unsubscribe-link").isEmpty shouldBe true
  }

  def checkUnsubscribedPageElements(document: Document): Unit = {
    document.getElementById("page-heading").text shouldBe "You are unsubscribed"
    document.getElementById("first-line").text shouldBe "You can change your email preferences at any time"
    checkLink(document, "setup-emailpreferences-link", "Set up email preferences", "/developer/profile/email-preferences/start")
    document.select("a#unsubscribe-link").isEmpty shouldBe true
  }

  "Email Preferences Summary view page" should {
    "render results when email preferences have been selected" in new Setup {

      val page =
        emailPreferencesSummaryView.render(
          EmailPreferencesSummaryViewData(apiCategoryDetailsMap, extendedServiceDetails), messagesProvider.messages, developerSession, request, appConfig)
      val document = Jsoup.parse(page.body)
      validateStaticElements(document)
      checkEmailPreferencesApisSection(document, developerSession.developer.emailPreferences, apiCategoryDetails, extendedServiceDetails)
      checkEmailTopicsSection(document, developerSession.developer.emailPreferences)

    }

    "display 'no email preferences selected' page for users that have not yet selected any" in new Setup {
      val page =
        emailPreferencesSummaryView.render(
          EmailPreferencesSummaryViewData(apiCategoryDetailsMap, Map.empty),
          messagesProvider.messages,
          developerSessionWithoutEmailPreferences,
          request,
          appConfig)

      val document = Jsoup.parse(page.body)
      validateStaticElements(document)
      checkNoEmailPreferencesPageElements(document)
    }


    "display 'unsubscribed' elements when user has unsubscribed" in new Setup {
      val page =
        emailPreferencesSummaryView.render(
          EmailPreferencesSummaryViewData(apiCategoryDetailsMap, Map.empty, unsubscribed = true),
          messagesProvider.messages,
          developerSessionWithoutEmailPreferences,
          request,
          appConfig)

      val document = Jsoup.parse(page.body)
      validateStaticElements(document)
      checkUnsubscribedPageElements(document)
    }
  }
}
