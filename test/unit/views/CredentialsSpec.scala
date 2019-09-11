/*
 * Copyright 2019 HM Revenue & Customs
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

package unit.views

import config.ApplicationConfig
import controllers.{PageData, VerifyPasswordForm}
import domain._
import org.joda.time.DateTime
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.OneServerPerSuite
import play.api.i18n.Messages.Implicits._
import play.api.test.FakeRequest
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.time.DateTimeUtils
import utils.CSRFTokenHelper._
import views.html.credentials

import scala.collection.JavaConversions._

class CredentialsSpec extends UnitSpec with OneServerPerSuite with MockitoSugar {
  trait Setup {
    val appConfig = mock[ApplicationConfig]

    def elementExistsByText(doc: Document, elementType: String, elementText: String): Boolean = {
      doc.select(elementType).exists(node => node.text.trim == elementText)
    }

    def elementExistsById(doc: Document, id: String) = doc.select(s"#$id").nonEmpty
  }

  "Credentials page" should {

    val request = FakeRequest().withCSRFToken

    val developer = Developer("Test", "Test", "Test", None, loggedInState = LoggedInState.LOGGED_IN)

    val clientSecret1 = ClientSecret("", "clientSecret1Content", DateTimeUtils.now)

    val clientSecret2 = ClientSecret("", "clientSecret2Content", DateTimeUtils.now)

    val emptyTokens = ApplicationTokens(EnvironmentToken("", Seq.empty, ""))

    val application = Application(
      "Test Application ID",
      "Test Application Client ID",
      "Test Application",
      DateTime.now(),
      Environment.PRODUCTION,
      Some("Test Application"),
      Set.empty,
      Standard(),
      false,
      ApplicationState.testing,
      None
    )

    val sandboxApplication = application.copy(deployedTo = Environment.SANDBOX)

    val form = VerifyPasswordForm.form

    val pageData = PageData(application, null, None)

    "render" in new Setup {

      val page = credentials.render(Role.ADMINISTRATOR, application, emptyTokens, form, request, developer, applicationMessages, appConfig, "credentials")

      page.contentType should include("text/html")

      val document = Jsoup.parse(page.body)

      elementExistsByText(document, "h1", "Manage credentials") shouldBe true
    }

    "show delete client secret button in production app if it has more than one client secret" in new Setup {

      val tokensWithTwoClientSecrets = emptyTokens.copy(production = EnvironmentToken("", Seq(clientSecret1, clientSecret2), ""))
      val productionApp = application.copy(state = ApplicationState.production("requester", "verificationCode"))
      val page = credentials.render(Role.ADMINISTRATOR, productionApp, tokensWithTwoClientSecrets, form, request, developer, applicationMessages, appConfig, "credentials")

      page.contentType should include ("text/html")

      val document = Jsoup.parse(page.body)
      elementExistsByText(document, "p", "To delete a client secret, you must add one first") shouldBe false
      elementExistsByText(document, "span", "Delete a client secret") shouldBe true
    }

    "not show delete client secret button in production app if it has only one client secret" in new Setup {

      val tokensWithOneClientSecret = emptyTokens.copy(production = EnvironmentToken("", Seq(clientSecret1), ""))
      val productionApp = application.copy(state = ApplicationState.production("requester", "verificationCode"))
      val page = credentials.render(Role.ADMINISTRATOR, productionApp, tokensWithOneClientSecret, form, request, developer, applicationMessages, appConfig, "credentials")

      page.contentType should include("text/html")

      val document = Jsoup.parse(page.body)
      elementExistsByText(document, "p", "To delete a client secret, you must add one first.") shouldBe true
      elementExistsByText(document, "span", "Delete a client secret") shouldBe false
    }

    "show delete client secret button in Sandbox app if it has more than one client secret" in new Setup {

      val tokensWithTwoClientSecrets = emptyTokens.copy(production = EnvironmentToken("", Seq(clientSecret1, clientSecret2), ""))

      val page = credentials.render(Role.ADMINISTRATOR, sandboxApplication, tokensWithTwoClientSecrets, form, request, developer, applicationMessages, appConfig, "credentials")

      page.contentType should include ("text/html")

      val document = Jsoup.parse(page.body)
      elementExistsByText(document, "p", "To delete a client secret, you must add one first") shouldBe false
      elementExistsByText(document, "button", "Delete a client secret") shouldBe true
    }

    "not show delete client secret button in Sandbox app if it has only one client secret" in new Setup {

      val tokensWithOneClientSecret = emptyTokens.copy(production = EnvironmentToken("", Seq(clientSecret1), ""))

      val page = credentials.render(Role.ADMINISTRATOR, sandboxApplication, tokensWithOneClientSecret, form, request, developer, applicationMessages, appConfig, "credentials")

      page.contentType should include("text/html")

      val document = Jsoup.parse(page.body)
      elementExistsByText(document, "p", "To delete a client secret, you must add one first.") shouldBe true
      elementExistsByText(document, "button", "Delete a client secret") shouldBe false
    }
  }
}
