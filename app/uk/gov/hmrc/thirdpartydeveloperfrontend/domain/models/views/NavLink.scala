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

package uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.views

import play.api.libs.json._
import uk.gov.hmrc.govukfrontend.views.html.components.Text
import uk.gov.hmrc.hmrcfrontend.views.viewmodels.header.NavigationItem

case class NavLink(label: String, href: String, truncate: Boolean = false, openInNewWindow: Boolean = false, isSensitive: Boolean = false)

object NavLink {
  implicit val format: OFormat[NavLink] = Json.format[NavLink]

}

case object StaticNavItems {

  def apply(apiDocumentationFrontendUrl: String, thirdPartyDeveloperFrontendUrl: String, devhubSupportFrontendUrl: String) = {
    Seq(
      NavigationItem(Text("Getting started"), Some(s"$apiDocumentationFrontendUrl/api-documentation/docs/using-the-hub")),
      NavigationItem(Text("API documentation"), Some(s"$apiDocumentationFrontendUrl/api-documentation/docs/api")),
      NavigationItem(Text("Applications"), Some(s"$thirdPartyDeveloperFrontendUrl/developer/applications")),
      NavigationItem(Text("Support"), Some(s"$devhubSupportFrontendUrl/devhub-support/")),
      NavigationItem(Text("Service availability"), Some("https://api-platform-status.production.tax.service.gov.uk/"), attributes = Map("target" -> "_blank"))
    )
  }
}

case object UserNavLinks {

  def apply(userFullName: Option[String], isRegistering: Boolean = false) =
    (userFullName, isRegistering) match {
      case (_, true)       => Seq.empty
      case (Some(name), _) => loggedInNavLinks(name)
      case (_, _)          => loggedOutNavLinks
    }

  private def loggedInNavLinks(userFullName: String) = List(
    NavLink(userFullName, "/developer/profile", isSensitive = true),
    NavLink("Sign out", "/developer/logout/survey")
  )

  private val loggedOutNavLinks = List(
    NavLink("Register", "/developer/registration"),
    NavLink("Sign in", "/developer/login")
  )
}
