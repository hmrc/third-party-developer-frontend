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
import java.util.UUID
import scala.jdk.CollectionConverters._

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import views.helper.CommonViewSpec
import views.html.{ClientSecretsGeneratedView, ClientSecretsView}

import play.api.test.FakeRequest
import play.twirl.api.Html

import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models.Access
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.{ApplicationState, ClientSecret, ClientSecretResponse, Collaborator, State}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{ApplicationId, ClientId, Environment}
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.DeveloperSessionBuilder
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils._
import uk.gov.hmrc.apiplatform.modules.tpd.test.data.DeveloperTestData

class ClientSecretsSpec extends CommonViewSpec with WithCSRFAddToken with CollaboratorTracker with LocalUserIdTracker
    with DeveloperSessionBuilder
    with DeveloperTestData
    with FixedClock {

  trait Setup {
    val clientSecretsView: ClientSecretsView                   = app.injector.instanceOf[ClientSecretsView]
    val clientSecretsGeneratedView: ClientSecretsGeneratedView = app.injector.instanceOf[ClientSecretsGeneratedView]

    def elementExistsByText(doc: Document, elementType: String, elementText: String): Boolean = {
      doc.select(elementType).asScala.exists(node => node.text.trim == elementText)
    }

    def elementContainsText(doc: Document, elementType: String, elementText: String): Boolean = {
      doc.select(elementType).asScala.exists(node => node.text.trim.contains(elementText))
    }

    def elementExistsById(doc: Document, id: String): Boolean = doc.select(s"#$id").asScala.nonEmpty
  }

  "Client secrets page" should {
    val request   = FakeRequest().withCSRFToken
    val developer = standardDeveloper.loggedIn

    val clientSecret1 = ClientSecretResponse(ClientSecret.Id.random, "", instant)
    val clientSecret2 = ClientSecretResponse(ClientSecret.Id.random, "", instant)
    val clientSecret3 = ClientSecretResponse(ClientSecret.Id.random, "", instant)
    val clientSecret4 = ClientSecretResponse(ClientSecret.Id.random, "", instant)
    val clientSecret5 = ClientSecretResponse(ClientSecret.Id.random, "", instant)

    val application = Application(
      ApplicationId(UUID.randomUUID()),
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
      state = ApplicationState(State.PRODUCTION, Some("requester@test.com"), Some("requester"), Some("verificationCode"), instant),
      checkInformation = None
    )

    "show generate a client secret button but no delete button when the app does not have any client secrets yet" in new Setup {
      val emptyClientSecrets: Seq[Nothing] = Seq.empty
      val page: Html                       = clientSecretsView.render(application, emptyClientSecrets, request, developer, messagesProvider, appConfig)

      page.contentType should include("text/html")

      val document: Document = Jsoup.parse(page.body)
      elementExistsByText(document, "button", "Generate a client secret") shouldBe true
      elementExistsByText(document, "a", "Delete") shouldBe false
    }

    "show generate another client secret button but no delete button when the app has only one client secret" in new Setup {
      val oneClientSecret: Seq[ClientSecretResponse] = Seq(clientSecret1)
      val page: Html                                 = clientSecretsView.render(application, oneClientSecret, request, developer, messagesProvider, appConfig)

      page.contentType should include("text/html")

      val document: Document = Jsoup.parse(page.body)
      elementExistsByText(document, "button", "Generate another client secret") shouldBe true
      elementExistsByText(document, "a", "Delete") shouldBe false
    }

    "show copy button when a new client secret has just been added" in new Setup {
      val aSecret    = "SomethingSecret"
      val page: Html = clientSecretsGeneratedView.render(application, application.id, aSecret, request, developer, messagesProvider, appConfig)

      page.contentType should include("text/html")

      val document: Document = Jsoup.parse(page.body)
      elementContainsText(document, "p", "Copy client secret") shouldBe true
      elementExistsByText(document, "p", "We only show you a new client secret once to help keep your data secure. Copy the client secret immediately.") shouldBe true
    }

    "not show copy button when a new client secret has not just been added" in new Setup {
      val oneClientSecret: Seq[ClientSecretResponse] = Seq(clientSecret1)

      val page: Html = clientSecretsView.render(application, oneClientSecret, request, developer, messagesProvider, appConfig)

      page.contentType should include("text/html")

      val document: Document = Jsoup.parse(page.body)
      elementExistsByText(document, "a", "Copy") shouldBe false
      elementContainsText(document, "p", "Copy the client secret immediately.") shouldBe false
      elementExistsByText(
        document,
        "p",
        "Your application must have at least one client secret. If you need to, you can generate a new client secret and delete old ones."
      ) shouldBe true
    }

    "show generate another client secret button and delete button when the app has more than one client secret" in new Setup {
      val twoClientSecrets: Seq[ClientSecretResponse] = Seq(clientSecret1, clientSecret2)
      val page: Html                                  = clientSecretsView.render(application, twoClientSecrets, request, developer, messagesProvider, appConfig)

      page.contentType should include("text/html")

      val document: Document = Jsoup.parse(page.body)
      elementExistsByText(document, "button", "Generate another client secret") shouldBe true
      elementContainsText(document, "a", "Delete") shouldBe true
    }

    "not show generate another client secret button when the app has reached the limit of 5 client secrets" in new Setup {
      val twoClientSecrets: Seq[ClientSecretResponse] = Seq(clientSecret1, clientSecret2, clientSecret3, clientSecret4, clientSecret5)
      val page: Html                                  = clientSecretsView.render(application, twoClientSecrets, request, developer, messagesProvider, appConfig)

      page.contentType should include("text/html")

      val document: Document = Jsoup.parse(page.body)
      elementExistsByText(document, "button", "Generate another client secret") shouldBe false
      elementExistsByText(
        document,
        "p",
        "You have the maximum number of client secrets for your application. You need to delete a client secret before you can generate a new one."
      ) shouldBe true
      elementContainsText(document, "a", "Delete") shouldBe true
    }
  }
}
