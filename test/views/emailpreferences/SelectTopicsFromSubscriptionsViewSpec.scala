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
import views.html.emailpreferences.SelectTopicsFromSubscriptionsView

import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest

import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.tpd.emailpreferences.domain.models.EmailTopic
import uk.gov.hmrc.apiplatform.modules.tpd.emailpreferences.domain.models.EmailTopic._
import uk.gov.hmrc.apiplatform.modules.tpd.sessions.domain.models.{LoggedInState, Session}
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder._
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.SelectTopicsFromSubscriptionsForm
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{LocalUserIdTracker, WithCSRFAddToken}

class SelectTopicsFromSubscriptionsViewSpec extends CommonViewSpec
    with WithCSRFAddToken
    with LocalUserIdTracker
    with DeveloperSessionBuilder
    with DeveloperTestData {

  trait Setup {

    val developerSessionWithoutEmailPreferences               = standardDeveloper.loggedIn
    implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withCSRFToken

    val applicationId: ApplicationId = ApplicationId.random

    val viewUnderTest = app.injector.instanceOf[SelectTopicsFromSubscriptionsView]
  }

  def validateStaticElements(document: Document, applicationId: ApplicationId): Unit = {
    document.getElementById("pageHeading").text() should be("Which topics do you want to receive information about?")
    // Check form is configured correctly
    val form = document.getElementById("emailPreferencesTopicsForm")
    form.attr("method") should be("POST")
    form.attr("action") should be(s"/developer/profile/email-preferences/topics-from-subscriptions?applicationId=${applicationId}")

    // check checkboxes are displayed
    validateCheckboxItemsAgainstTopics(document)

    // Check submit button is correct
    document.getElementById("selectTopics").text should be("Continue")
  }

  def checkLink(document: Document, id: String, linkText: String, linkVal: String) = {
    withClue(s"Link text not as expected: for element: $id") {
      document.getElementById(id).text().startsWith(linkText) shouldBe true
    }
    document.getElementById(id).attr("href") shouldBe linkVal
  }

  def validateCheckboxItemsAgainstTopics(document: Document) = {
    EmailTopic.values.foreach(topic => {
      val checkbox = document.getElementById(topic.toString)
      checkbox.attr("name") shouldBe "topic[]"
      checkbox.`val`() shouldBe topic.toString
      document.select(s"label[for=${topic.toString}]").text.startsWith(topic.displayName) shouldBe true
      document.select(s"label[for=${topic.toString}] > div[class=govuk-hint]").text() shouldBe topic.description
    })
  }

  "Email Preferences Select Topics view page" should {

    "render the topics selection Page with no check boxes selected when no user selected topics passed into the view" in new Setup {
      val page     =
        viewUnderTest.render(
          SelectTopicsFromSubscriptionsForm.form,
          Set.empty,
          applicationId,
          messagesProvider.messages,
          developerSessionWithoutEmailPreferences,
          request,
          appConfig
        )
      val document = Jsoup.parse(page.body)
      validateStaticElements(document, applicationId)
      document.select("input[type=checkbox][checked]").asScala.toList shouldBe List.empty
    }

    "render the topics selection Page with boxes selected when user selected topics passed to the view" in new Setup {
      val usersTopics = Set(BUSINESS_AND_POLICY.toString, EVENT_INVITES.toString)
      val page        =
        viewUnderTest.render(
          SelectTopicsFromSubscriptionsForm.form,
          usersTopics,
          applicationId,
          messagesProvider.messages,
          developerSessionWithoutEmailPreferences,
          request,
          appConfig
        )
      val document    = Jsoup.parse(page.body)
      validateStaticElements(document, applicationId)

      val selectedBoxes = document.select("input[type=checkbox][checked]").asScala.toList
      selectedBoxes.map(_.attr("value")) should contain allElementsOf usersTopics
    }
  }
}
