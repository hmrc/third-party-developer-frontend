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

import java.time.Period

import org.jsoup.Jsoup
import views.helper.CommonViewSpec
import views.html.DeleteSubordinateApplicationConfirmView

import play.api.test.FakeRequest

import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models.Access
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.{ApplicationState, RedirectUri, State}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{ApplicationId, ClientId, Environment}
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.apiplatform.modules.tpd.builder.DeveloperSessionBuilder
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.{LoggedInState, UserSession}
import uk.gov.hmrc.apiplatform.modules.tpd.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.ViewHelpers.elementExistsByText
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{CollaboratorTracker, WithCSRFAddToken}

class DeleteSubordinateApplicationConfirmSpec extends CommonViewSpec with WithCSRFAddToken with LocalUserIdTracker with DeveloperSessionBuilder with DeveloperTestData
    with CollaboratorTracker
    with FixedClock {

  val deleteSubordinateApplicationConfirmView = app.injector.instanceOf[DeleteSubordinateApplicationConfirmView]

  "delete application confirm page" should {

    val request           = FakeRequest().withCSRFToken
    val appId             = ApplicationId.random
    val clientId          = ClientId("clientId123")
    val loggedInDeveloper = standardDeveloper.loggedIn
    val application       = Application(
      appId,
      clientId,
      "App name 1",
      instant,
      Some(instant),
      None,
      Period.ofDays(547),
      Environment.SANDBOX,
      Some("Description 1"),
      Set(loggedInDeveloper.email.asAdministratorCollaborator),
      state = ApplicationState(State.PRODUCTION, Some(loggedInDeveloper.email.text), Some(loggedInDeveloper.displayedName), Some(""), instant),
      access = Access.Standard(redirectUris = List("https://red1", "https://red2").map(RedirectUri.unsafeApply), termsAndConditionsUrl = Some("http://tnc-url.com"))
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
