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

package views

import domain._
import org.mockito.BDDMockito.given
import play.twirl.api.{Html, HtmlFormat}
import views.helper.CommonViewSpec
import views.html.include.Main

class MainTemplateSpec extends CommonViewSpec {

  "MainTemplateSpec" should {
    val mainView = app.injector.instanceOf[Main]
    val developer = Developer("email", "firstName", "lastName")
    val session = Session("sessionId", developer, LoggedInState.LOGGED_IN)
    implicit val developerSession = DeveloperSession(session)

    "Application title meta data set by configuration" in {
      given(appConfig.title).willReturn("Application Title")

      val view: Html = mainView.render(
        title = "Test",
        navTitle = None,
        navTitleLink = None,
        headerNavLinks = HtmlFormat.empty,
        contentHeader = None,
        sidebar = None,
        serviceInfoContent = None,
        fullWidthBanner = None,
        leftNav = None,
        breadcrumbs = Seq.empty,
        back = NoBackButton,
        fullWidthContent = false,
        developerSession = Some(developerSession),
        mainContent = HtmlFormat.empty,
        messagesProvider = messagesProvider,
        applicationConfig = appConfig)

      view.body should include("data-title=\"Application Title")
    }
  }
}
