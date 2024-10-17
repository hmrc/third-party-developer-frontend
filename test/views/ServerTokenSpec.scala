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

import java.util.UUID.randomUUID
import scala.jdk.CollectionConverters._

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import views.helper.CommonViewSpec
import views.html.ServerTokenView

import play.api.test.FakeRequest
import play.twirl.api.Html

import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models.Access
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.{ApplicationState, ApplicationWithCollaboratorsFixtures, State}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{ApplicationId, ClientId, Environment}
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.{LoggedInState, UserSession}
import uk.gov.hmrc.apiplatform.modules.tpd.test.builders.UserBuilder
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.DeveloperSessionBuilder
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils._

class ServerTokenSpec
    extends CommonViewSpec
    with WithCSRFAddToken
    with CollaboratorTracker
    with LocalUserIdTracker
    with DeveloperSessionBuilder
    with UserBuilder
    with FixedClock
    with ApplicationWithCollaboratorsFixtures {

  trait Setup {
    val appConfig: ApplicationConfig = mock[ApplicationConfig]

    def elementExistsByText(doc: Document, elementType: String, elementText: String): Boolean = {
      doc.select(elementType).asScala.exists(node => node.text.trim == elementText)
    }
  }

  "Server token page" should {
    val request          = FakeRequest().withCSRFToken
    val developerSession = buildUser("Test".toLaxEmail, "Test", "Test").loggedIn

    "render" in new Setup {
      val serverTokenView = app.injector.instanceOf[ServerTokenView]
      val page: Html      = serverTokenView.render(standardApp, randomUUID.toString, request, developerSession, messagesProvider, appConfig)

      page.contentType should include("text/html")
      val document: Document = Jsoup.parse(page.body)
      elementExistsByText(document, "h1", "Server token") shouldBe true
    }
  }
}
