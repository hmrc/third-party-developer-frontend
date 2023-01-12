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

package views

import java.time.{LocalDateTime, Period, ZoneOffset}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.LoggedInState
import org.jsoup.Jsoup
import play.api.test.FakeRequest
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.ViewHelpers.elementExistsByText
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.WithCSRFAddToken
import views.helper.CommonViewSpec
import views.html.DeleteSubordinateApplicationConfirmView
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.{DeveloperBuilder, DeveloperSessionBuilder}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.LocalUserIdTracker

class DeleteSubordinateApplicationConfirmSpec extends CommonViewSpec with WithCSRFAddToken with DeveloperBuilder with LocalUserIdTracker with DeveloperSessionBuilder {

  val deleteSubordinateApplicationConfirmView = app.injector.instanceOf[DeleteSubordinateApplicationConfirmView]

  "delete application confirm page" should {

    val request           = FakeRequest().withCSRFToken
    val appId             = ApplicationId("1234")
    val clientId          = ClientId("clientId123")
    val loggedInDeveloper = buildDeveloperSession(loggedInState = LoggedInState.LOGGED_IN, buildDeveloper("developer@example.com", "John", "Doe"))
    val application       = Application(
      appId,
      clientId,
      "App name 1",
      LocalDateTime.now(ZoneOffset.UTC),
      Some(LocalDateTime.now(ZoneOffset.UTC)),
      None,
      Period.ofDays(547),
      Environment.SANDBOX,
      Some("Description 1"),
      Set(loggedInDeveloper.email.asAdministratorCollaborator),
      state = ApplicationState.production(loggedInDeveloper.email, loggedInDeveloper.displayedName, ""),
      access = Standard(redirectUris = List("https://red1", "https://red2"), termsAndConditionsUrl = Some("http://tnc-url.com"))
    )

    "show link and text to confirm deletion" in {

      val page = deleteSubordinateApplicationConfirmView.render(application, request, loggedInDeveloper, messagesProvider, appConfig)
      page.contentType should include("text/html")

      val document = Jsoup.parse(page.body)
      elementExistsByText(document, "h1", "Are you sure you want us to delete your application?") shouldBe true
      elementExistsByText(document, "p", "This will be deleted immediately. We cannot restore applications once they have been deleted.") shouldBe true

    }

  }
}
