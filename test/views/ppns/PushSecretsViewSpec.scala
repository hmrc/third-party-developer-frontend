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

package views.ppns

import java.time.LocalDateTime
import scala.collection.JavaConverters._
import cats.data.NonEmptyList
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import views.helper.CommonViewSpec
import views.html.ppns.PushSecretsView
import play.api.test.FakeRequest
import play.twirl.api.Html
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.LoggedInState
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils._

class PushSecretsViewSpec extends CommonViewSpec
    with WithCSRFAddToken
    with CollaboratorTracker
    with LocalUserIdTracker
    with DeveloperSessionBuilder 
    with DeveloperTestData {

  trait Setup {
    val pushSecretsView: PushSecretsView = app.injector.instanceOf[PushSecretsView]

    def elementExistsByText(doc: Document, elementType: String, elementText: String): Boolean = {
      doc.select(elementType).asScala.exists(node => node.text.trim == elementText)
    }
  }

  "Push secrets page" should {
    val request   = FakeRequest().withCSRFToken

    val application                       = Application(
      ApplicationId("Test Application ID"),
      ClientId("Test Application Client ID"),
      "Test Application",
      LocalDateTime.now(),
      Some(LocalDateTime.now()),
      None,
      grantLength,
      Environment.PRODUCTION,
      Some("Test Application"),
      collaborators = Set(standardDeveloper.email.asAdministratorCollaborator),
      access = Standard(),
      state = ApplicationState.testing,
      checkInformation = None
    )
    val pushSecrets: NonEmptyList[String] = NonEmptyList.one("the secret")

    "render" in new Setup {
      val page: Html = pushSecretsView.render(application, pushSecrets, request, standardDeveloper.loggedIn, messagesProvider, appConfig)

      page.contentType should include("text/html")
      val document: Document = Jsoup.parse(page.body)
      elementExistsByText(document, "h1", "Push secret") shouldBe true
    }
  }
}
