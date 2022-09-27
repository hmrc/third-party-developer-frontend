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

package views

import org.jsoup.Jsoup
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.DeveloperBuilder
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.{DeveloperSession, LoggedInState, Session}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.views.{GenericFeedbackBanner, NoBackButton}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.Crumb
import play.twirl.api.{Html, HtmlFormat}
import play.api.test.FakeRequest
import views.helper.CommonViewSpec
import views.html.include.Main
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.ViewHelpers.elementExistsById

class MainTemplateSpec extends CommonViewSpec with DeveloperBuilder with LocalUserIdTracker {

  "MainTemplateSpec" should {
    val mainView = app.injector.instanceOf[Main]
    val developer = buildDeveloper()
    val session = Session("sessionId", developer, LoggedInState.LOGGED_IN)
    implicit val developerSession = DeveloperSession(session)
    implicit val request = FakeRequest()

    "Application title meta data set by configuration" in {
      when(appConfig.title).thenReturn("Application Title")

      val view: Html = mainView.render(
        title = "Test",
        navTitleLink = None,
        userFullName = None,
        isRegistering = false,
        breadcrumbs = Seq.empty,
        leftNav = None,
        fullWidth = true,
        back = NoBackButton,
        fullWidthContent = true,
        feedbackBanner = Some(GenericFeedbackBanner),
        mainContent = HtmlFormat.empty,
        request = request,
        messages = messagesProvider.messages,
        applicationConfig = appConfig
      )

      view.body should include("data-title=\"Application Title")

      val document = Jsoup.parse(view.body)

      elementExistsById(document, "feedback") shouldBe true
      document.getElementById("feedback-title").text() shouldBe "Your feedback helps us improve our service"
    }
  }
}
