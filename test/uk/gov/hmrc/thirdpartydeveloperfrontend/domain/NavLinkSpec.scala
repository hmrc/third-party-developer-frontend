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

package uk.gov.hmrc.thirdpartydeveloperfrontend.domain

import uk.gov.hmrc.govukfrontend.views.html.components.Text
import uk.gov.hmrc.hmrcfrontend.views.viewmodels.header.NavigationItem

import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.views.{NavLink, StaticNavItems, UserNavLinks}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.AsyncHmrcSpec

class NavLinkSpec extends AsyncHmrcSpec {

  "NavigationHelper" should {
    "return user nav links if username is given" in {
      UserNavLinks(Some("User Name")) shouldBe
        Seq(NavLink("User Name", "/developer/profile", isSensitive = true), NavLink("Sign out", "/developer/logout/survey"))
    }

    "return logged out nav links if username is not given" in {
      UserNavLinks(None) shouldBe
        Seq(NavLink("Register", "/developer/registration"), NavLink("Sign in", "/developer/login"))
    }

    "return static navigation items for devhub" in {
      StaticNavItems("http://localhost:9680", "http://localhost:9685", "http://localhost:9695") shouldBe
        Seq(
          NavigationItem(Text("Getting started"), Some(s"http://localhost:9680/api-documentation/docs/using-the-hub")),
          NavigationItem(Text("API documentation"), Some(s"http://localhost:9680/api-documentation/docs/api")),
          NavigationItem(Text("Applications"), Some(s"http://localhost:9685/developer/applications")),
          NavigationItem(Text("Support"), Some(s"http://localhost:9695/devhub-support/")),
          NavigationItem(Text("Service availability"), Some("https://api-platform-status.production.tax.service.gov.uk/"), attributes = Map("target" -> "_blank"))
        )
    }
  }
}
