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

import domain._
import domain.Environment.PRODUCTION
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.scalatestplus.play.OneServerPerSuite
import play.api.test.FakeRequest
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.time.DateTimeUtils.now
import views.html.include.leftHandNav
import scala.collection.JavaConversions._

class LeftHandNavSpec extends UnitSpec with OneServerPerSuite {

  trait Setup {
    implicit val request = FakeRequest()
    val standardApplication = Application("std-app-id", "std-client-id", "name", now, PRODUCTION, access = Standard())
    val privilegedApplication = Application("std-app-id", "std-client-id", "name", now, PRODUCTION, access = Privileged())
    val ropcApplication = Application("std-app-id", "std-client-id", "name", now, PRODUCTION, access = ROPC())

    def elementExistsById(doc: Document, id: String) = doc.select(s"#$id").nonEmpty
  }

  "Left Hand Nav" should {

    "include links to manage API subscriptions, credentials and team members for an app with standard access" in new Setup {
      val document = Jsoup.parse(leftHandNav(Some(standardApplication), Some("")).body)

      elementExistsById(document, "nav-manage-subscriptions") shouldBe true
      elementExistsById(document, "nav-manage-credentials") shouldBe true
      elementExistsById(document, "nav-manage-team") shouldBe true
      elementExistsById(document, "nav-delete-application") shouldBe true
    }

    "not include links to manage API subscriptions, credentials and team members for an app with privileged access" in new Setup {
      val document = Jsoup.parse(leftHandNav(Some(privilegedApplication), Some("")).body)

      elementExistsById(document, "nav-manage-subscriptions") shouldBe false
      elementExistsById(document, "nav-manage-credentials") shouldBe false
      elementExistsById(document, "nav-manage-team") shouldBe false
      elementExistsById(document, "nav-delete-application") shouldBe false
    }

    "not include links to manage API subscriptions, credentials and team members for an app with ROPC access" in new Setup {
      val document = Jsoup.parse(leftHandNav(Some(ropcApplication), Some("")).body)

      elementExistsById(document, "nav-manage-subscriptions") shouldBe false
      elementExistsById(document, "nav-manage-credentials") shouldBe false
      elementExistsById(document, "nav-manage-team") shouldBe false
      elementExistsById(document, "nav-delete-application") shouldBe false
    }
  }
}
