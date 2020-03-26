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

import config.ApplicationConfig
import domain._
import org.joda.time.DateTime
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.OneServerPerSuite
import play.api.i18n.Messages.Implicits._
import play.api.test.FakeRequest
import play.twirl.api.Html
import uk.gov.hmrc.play.test.UnitSpec
import utils.CSRFTokenHelper._
import utils.SharedMetricsClearDown
import views.html.clientId

import scala.collection.JavaConversions._

class ClientIdSpec extends UnitSpec with OneServerPerSuite with SharedMetricsClearDown with MockitoSugar {
  trait Setup {
    val appConfig: ApplicationConfig = mock[ApplicationConfig]

    def elementExistsByText(doc: Document, elementType: String, elementText: String): Boolean = {
      doc.select(elementType).exists(node => node.text.trim == elementText)
    }
  }

  "Client ID page" should {
    val request = FakeRequest().withCSRFToken
    val developer = utils.DeveloperSession("Test", "Test", "Test", None, loggedInState = LoggedInState.LOGGED_IN)

    val application = Application(
      "Test Application ID",
      "Test Application Client ID",
      "Test Application",
      DateTime.now(),
      DateTime.now(),
      Environment.PRODUCTION,
      Some("Test Application"),
      collaborators = Set(Collaborator(developer.email, Role.ADMINISTRATOR)),
      access = Standard(),
      state = ApplicationState.testing,
      checkInformation = None
    )

    "render" in new Setup {
      val page: Html = clientId.render(application, request, developer, applicationMessages, appConfig)

      page.contentType should include("text/html")
      val document: Document = Jsoup.parse(page.body)
      elementExistsByText(document, "h1", "Client ID") shouldBe true
    }
  }
}
