/*
 * Copyright 2021 HM Revenue & Customs
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

package domain.models.views

import play.api.libs.json.Json

case class NavLink(label: String, href: String, truncate: Boolean = false, openInNewWindow: Boolean = false)

object NavLink {
  implicit val format = Json.format[NavLink]
}

case object StaticNavLinks {
  def apply(apiDocumentationFrontendUrl: String, thirdPartyDeveloperFrontendUrl: String) = {
    Seq(
      NavLink("Documentation", s"$apiDocumentationFrontendUrl/api-documentation/docs/using-the-hub"),
      NavLink("Applications", s"$thirdPartyDeveloperFrontendUrl/developer/applications"),
      NavLink("Support", s"$thirdPartyDeveloperFrontendUrl/developer/support"),
      NavLink("Service availability", "https://api-platform-status.production.tax.service.gov.uk/", openInNewWindow = true))
  }
}

case object UserNavLinks {

  def apply(userFullName: Option[String], isRegistering: Boolean = false) =
    (userFullName, isRegistering) match {
      case (_, true) => Seq.empty
      case (Some(name), _) => loggedInNavLinks(name)
      case (_, _) => loggedOutNavLinks
    }

  private def loggedInNavLinks(userFullName: String) = Seq(
    NavLink(userFullName,"/developer/profile"),
    NavLink("Sign out","/developer/logout/survey")
  )

  private val loggedOutNavLinks = Seq(
    NavLink("Register", "/developer/registration"),
    NavLink("Sign in", "/developer/login")
  )
}
