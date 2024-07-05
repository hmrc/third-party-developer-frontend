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
import scala.jdk.CollectionConverters._

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import views.helper.CommonViewSpec
import views.html.CredentialsView

import play.api.test.FakeRequest
import play.twirl.api.Html

import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models.Access
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.{ApplicationState, Collaborator, State}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{ApplicationId, ClientId, Environment}
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.apiplatform.modules.tpd.domain.models.LoggedInState
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils._

class CredentialsSpec extends CommonViewSpec
    with WithCSRFAddToken
    with CollaboratorTracker
    with LocalUserIdTracker
    with DeveloperSessionBuilder
    with DeveloperTestData
    with FixedClock {

  trait Setup {
    val credentialsView = app.injector.instanceOf[CredentialsView]

    def elementExistsByText(doc: Document, elementType: String, elementText: String): Boolean = {
      doc.select(elementType).asScala.exists(node => node.text.trim == elementText)
    }
  }

  "Credentials page" should {
    val request   = FakeRequest().withCSRFToken
    val developer = standardDeveloper.loggedIn

    val application = Application(
      ApplicationId.random,
      ClientId("Test Application Client ID"),
      "Test Application",
      instant,
      Some(instant),
      None,
      Period.ofDays(547),
      Environment.PRODUCTION,
      Some("Test Application"),
      collaborators = Set(developer.email.asAdministratorCollaborator),
      access = Access.Standard(),
      state = ApplicationState(State.PRODUCTION, Some(""), Some(""), Some(""), instant),
      checkInformation = None
    )

    val sandboxApplication = application.copy(deployedTo = Environment.SANDBOX)

    "display the credentials page for admins" in new Setup {
      val page: Html = credentialsView.render(application, request, developer, messagesProvider, appConfig)

      page.contentType should include("text/html")
      val document: Document = Jsoup.parse(page.body)
      elementExistsByText(document, "h1", "Credentials") shouldBe true
      elementExistsByText(document, "a", "Continue") shouldBe true
    }

    "display the credentials page for non admins if the app is in sandbox" in new Setup {
      val developerApp: Application = sandboxApplication.copy(collaborators = Set(developer.email.asDeveloperCollaborator))
      val page: Html                = credentialsView.render(developerApp, request, developer, messagesProvider, appConfig)

      page.contentType should include("text/html")
      val document: Document = Jsoup.parse(page.body)
      elementExistsByText(document, "h1", "Credentials") shouldBe true
      elementExistsByText(document, "a", "Continue") shouldBe true
    }

    "tell the user they don't have access to credentials when the logged in user is not an admin and the app is not in sandbox" in new Setup {
      val developerApp: Application = application.copy(collaborators = Set(developer.email.asDeveloperCollaborator))
      val page: Html                = credentialsView.render(developerApp, request, developer, messagesProvider, appConfig)

      page.contentType should include("text/html")
      val document: Document = Jsoup.parse(page.body)
      elementExistsByText(document, "h1", "Credentials") shouldBe true
      elementExistsByText(document, "a", "Continue") shouldBe false
      elementExistsByText(document, "div", "You cannot view or edit production credentials because you're not an administrator.") shouldBe true
    }
  }
}
