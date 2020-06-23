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

import domain.Environment.PRODUCTION
import domain._
import model.ApplicationViewModel
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.scalatestplus.play.OneServerPerSuite
import play.api.test.FakeRequest
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.time.DateTimeUtils.now
import utils.SharedMetricsClearDown
import views.html.include.leftHandNav

import scala.collection.JavaConversions._

class LeftHandNavSpec extends UnitSpec with OneServerPerSuite with SharedMetricsClearDown {

  trait Setup {
    implicit val request = FakeRequest()
    implicit val loggedIn = utils.DeveloperSession("user@example.com", "Test", "Test", None, loggedInState = LoggedInState.LOGGED_IN)
    val standardApplication = Application("std-app-id", "std-client-id", "name", now, now, None, PRODUCTION, access = Standard())
    val privilegedApplication = Application("std-app-id", "std-client-id", "name", now, now, None, PRODUCTION, access = Privileged())
    val ropcApplication = Application("std-app-id", "std-client-id", "name", now, now, None, PRODUCTION, access = ROPC())

    def elementExistsById(doc: Document, id: String) = doc.select(s"#$id").nonEmpty
  }

  "Left Hand Nav" should {

    "include links to manage API subscriptions, credentials and team members for an app with standard access" in new Setup {
      val document: Document = Jsoup.parse(leftHandNav(Some(ApplicationViewModel(standardApplication, hasSubscriptionsFields = false)), Some("")).body)

      elementExistsById(document, "nav-manage-subscriptions") shouldBe true
      elementExistsById(document, "nav-manage-credentials") shouldBe true
      elementExistsById(document, "nav-manage-client-id") shouldBe false
      elementExistsById(document, "nav-manage-client-secrets") shouldBe false
      elementExistsById(document, "nav-manage-team") shouldBe true
      elementExistsById(document, "nav-delete-application") shouldBe true
    }

    "include links to manage team members but not API subscriptions for an app with privileged access" in new Setup {
      val document: Document = Jsoup.parse(leftHandNav(Some(ApplicationViewModel(privilegedApplication, hasSubscriptionsFields = false)), Some("")).body)

      elementExistsById(document, "nav-manage-subscriptions") shouldBe false
      elementExistsById(document, "nav-manage-credentials") shouldBe true
      elementExistsById(document, "nav-manage-client-id") shouldBe false
      elementExistsById(document, "nav-manage-client-secrets") shouldBe false
      elementExistsById(document, "nav-manage-team") shouldBe true
      elementExistsById(document, "nav-delete-application") shouldBe false
    }

    "include links to manage team members but not API subscriptions for an app with ROPC access" in new Setup {
      val document: Document = Jsoup.parse(leftHandNav(Some(ApplicationViewModel(ropcApplication, hasSubscriptionsFields = false)), Some("")).body)

      elementExistsById(document, "nav-manage-subscriptions") shouldBe false
      elementExistsById(document, "nav-manage-credentials") shouldBe true
      elementExistsById(document, "nav-manage-client-id") shouldBe false
      elementExistsById(document, "nav-manage-client-secrets") shouldBe false
      elementExistsById(document, "nav-manage-team") shouldBe true
      elementExistsById(document, "nav-delete-application") shouldBe false
    }

    "include links to client ID and client secrets if the user is an admin and the app has reached production state" in new Setup {
      val application = standardApplication.copy(
        collaborators = Set(Collaborator(loggedIn.email, Role.ADMINISTRATOR)),
        state = ApplicationState.production("", ""))

      val document: Document = Jsoup.parse(leftHandNav(Some(ApplicationViewModel(application, hasSubscriptionsFields = false)), Some("")).body)

      elementExistsById(document, "nav-manage-client-id") shouldBe true
      elementExistsById(document, "nav-manage-client-secrets") shouldBe true
    }

    "include links to client ID and client secrets if the user is not an admin but the app is in sandbox" in new Setup {
      val application = standardApplication.copy(
        deployedTo = Environment.SANDBOX,
        collaborators = Set(Collaborator(loggedIn.email, Role.DEVELOPER)),
        state = ApplicationState.production("", ""))

      val document: Document = Jsoup.parse(leftHandNav(Some(ApplicationViewModel(application, hasSubscriptionsFields = false)), Some("")).body)

      elementExistsById(document, "nav-manage-client-id") shouldBe true
      elementExistsById(document, "nav-manage-client-secrets") shouldBe true
    }
  }
}
