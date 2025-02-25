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
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.{ApplicationState, ApplicationWithCollaborators, ApplicationWithCollaboratorsFixtures, Collaborator, State}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{ApplicationId, ClientId, Environment}
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.{LoggedInState, UserSession}
import uk.gov.hmrc.apiplatform.modules.tpd.test.data.UserTestData
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.DeveloperSessionBuilder
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils._

class CredentialsSpec extends CommonViewSpec
    with WithCSRFAddToken
    with CollaboratorTracker
    with LocalUserIdTracker
    with DeveloperSessionBuilder
    with UserTestData
    with FixedClock
    with ApplicationWithCollaboratorsFixtures {

  trait Setup {
    val credentialsView = app.injector.instanceOf[CredentialsView]

    def elementExistsByText(doc: Document, elementType: String, elementText: String): Boolean = {
      doc.select(elementType).asScala.exists(node => node.text.trim == elementText)
    }
  }

  "Credentials page" should {
    val request          = FakeRequest().withCSRFToken
    val developerSession = standardDeveloper.loggedIn

    val application = standardApp.withCollaborators(developerSession.developer.email.asAdministratorCollaborator)

    val sandboxApplication = application.inSandbox()

    "display the credentials page for admins" in new Setup {
      val page: Html = credentialsView.render(application, request, developerSession, messagesProvider, appConfig)

      page.contentType should include("text/html")
      val document: Document = Jsoup.parse(page.body)
      elementExistsByText(document, "h1", "Credentials") shouldBe true
      elementExistsByText(document, "a", "Continue") shouldBe true
    }

    "display the credentials page for non admins if the app is in sandbox" in new Setup {
      val developerApp: ApplicationWithCollaborators = sandboxApplication.copy(collaborators = Set(developerSession.developer.email.asDeveloperCollaborator))
      val page: Html                                 = credentialsView.render(developerApp, request, developerSession, messagesProvider, appConfig)

      page.contentType should include("text/html")
      val document: Document = Jsoup.parse(page.body)
      elementExistsByText(document, "h1", "Credentials") shouldBe true
      elementExistsByText(document, "a", "Continue") shouldBe true
    }

    "tell the user they don't have access to credentials when the logged in user is not an admin and the app is not in sandbox" in new Setup {
      val developerApp: ApplicationWithCollaborators = application.copy(collaborators = Set(developerSession.developer.email.asDeveloperCollaborator))
      val page: Html                                 = credentialsView.render(developerApp, request, developerSession, messagesProvider, appConfig)

      page.contentType should include("text/html")
      val document: Document = Jsoup.parse(page.body)
      elementExistsByText(document, "h1", "Credentials") shouldBe true
      elementExistsByText(document, "a", "Continue") shouldBe false
      elementExistsByText(document, "div", "You cannot view or edit production credentials because you're not an administrator.") shouldBe true
    }
  }
}
