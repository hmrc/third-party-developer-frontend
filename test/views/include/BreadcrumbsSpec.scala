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

package views.include

import config.ApplicationConfig
import domain.{Application, Environment}
import model.Crumb
import org.jsoup.Jsoup
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.time.DateTimeUtils
import utils.SharedMetricsClearDown

import scala.concurrent.ExecutionContext.Implicits.global

class BreadcrumbsSpec extends UnitSpec with GuiceOneServerPerSuite with SharedMetricsClearDown with MockitoSugar {

  val appConfig = mock[ApplicationConfig]

  "breadcrumbs" should {
    "render in the right order" in {

      val applicationName = "An Application Name"
      val application = Application("appId123", "clientId123", applicationName, DateTimeUtils.now, DateTimeUtils.now, None, Environment.PRODUCTION)
      val crumbs: Array[Crumb] = Array(Crumb("Another Breadcrumb"), Crumb.application(application), Crumb.viewAllApplications, Crumb.home(appConfig))

      val page = views.html.include.breadcrumbs.render(crumbs)

      page.contentType should include("text/html")

      val document = Jsoup.parse(page.body)
      val breadcrumbText = await(document.body.select("li").map(_.text))

      breadcrumbText shouldBe List("Home", "View all applications", "An Application Name", "Another Breadcrumb").mkString(" ")

    }
  }

}
