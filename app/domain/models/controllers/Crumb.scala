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

package domain.models.controllers

import controllers.routes
import config.ApplicationConfig
import domain.models.applications.Application

case class Crumb(name: String, url: String = "", dataAttribute: Option[String] = None)

object Crumb {

  def home(implicit applicationConfig: ApplicationConfig) =
    Crumb("Home", s"${applicationConfig.apiDocumentationFrontendUrl}/api-documentation", Some("data-breadcrumb-home"))

  val viewAllApplications =
    Crumb("View all applications",s"${routes.ManageApplications.manageApps}", Some("data-breadcrumb-manage-app"))

  val protectAccount =
    Crumb("Protect account",s"${controllers.profile.routes.ProtectAccount.getProtectAccount}", Some("data-breadcrumb-protect-account"))

  val signIn =
    Crumb("Sign in",s"${routes.UserLoginAccount.login()}", Some("data-breadcrumb-sign-in"))

  def application(application: Application) =
    Crumb(s"${application.name}", s"${routes.Details.details(application.id)}", Some("data-breadcrumb-app-name"))

  def applicationMetadata(application: Application) =
    Crumb("Subscription configuration", s"${routes.ManageSubscriptions.listApiSubscriptions(application.id)}", Some("data-breadcrumb-app-metadata"))

  def manageProfile = Crumb("Manage profile", s"${controllers.profile.routes.Profile.showProfile()}", Some("data-breadcrumb-manage-profile"))

  def emailPreferences = Crumb("Email preferences", s"${controllers.profile.routes.EmailPreferences.emailPreferencesSummaryPage()}", Some("data-breadcrumb-email-preferences"))

  def ipAllowlist(application: Application) = Crumb("IP allow list", s"${routes.IpAllowlist.viewIpAllowlist(application.id)}", Some("data-breadcrumb-ip-allowlist"))
}
