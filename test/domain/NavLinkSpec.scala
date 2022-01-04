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

package domain

import domain.models.views.{NavLink, StaticNavLinks, UserNavLinks}
import utils.AsyncHmrcSpec

class NavLinkSpec extends AsyncHmrcSpec {

  "NavigationHelper" should {
    "return user nav links if username is given" in {
      UserNavLinks(Some("User Name")) shouldBe
        Seq(NavLink("User Name", "/developer/profile"), NavLink("Sign out", "/developer/logout/survey"))
    }

    "return logged out nav links if username is not given" in {
      UserNavLinks(None) shouldBe
        Seq(NavLink("Register", "/developer/registration"), NavLink("Sign in", "/developer/login"))
    }

    "return static navlinks for devhub" in {
      StaticNavLinks("http://localhost:9680", "http://localhost:9685") shouldBe
        Seq(
          NavLink("Documentation", "http://localhost:9680/api-documentation/docs/using-the-hub"),
          NavLink("Applications", "http://localhost:9685/developer/applications"),
          NavLink("Support", "http://localhost:9685/developer/support"),
          NavLink("Service availability", "https://api-platform-status.production.tax.service.gov.uk/", openInNewWindow = true)
        )
    }
  }
}
