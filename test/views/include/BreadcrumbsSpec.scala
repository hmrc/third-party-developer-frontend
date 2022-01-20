/*
 * Copyright 2022 HM Revenue & Customs
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

import java.time.Period
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.{Application, ApplicationId, ClientId, Environment}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.Crumb
import org.jsoup.Jsoup
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.test.Helpers.{contentAsString, contentType}
import play.twirl.api.Html
import uk.gov.hmrc.time.DateTimeUtils
import utils.{AsyncHmrcSpec, SharedMetricsClearDown}

class BreadcrumbsSpec extends AsyncHmrcSpec with GuiceOneServerPerSuite with SharedMetricsClearDown {

  val appConfig = mock[ApplicationConfig]

  "breadcrumbs" should {
    "render in the right order" in {

      val applicationName = "An Application Name"
      val application = Application(ApplicationId("appId123"), ClientId("clientId123"), applicationName, DateTimeUtils.now, DateTimeUtils.now, None, grantLength = Period.ofDays(547), Environment.PRODUCTION)
      val crumbs = Array(Crumb("Another Breadcrumb"), Crumb.application(application), Crumb.viewAllApplications, Crumb.home(appConfig))

      val page: Html = views.html.include.breadcrumbs.render(crumbs)

      contentType(page) shouldBe "text/html"

      val document = Jsoup.parse(contentAsString(page))
      val breadcrumbText = document.body.select("li").text()

      breadcrumbText shouldBe List("Home", "View all applications", "An Application Name", "Another Breadcrumb").mkString(" ")

    }
  }

}
