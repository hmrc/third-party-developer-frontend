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

package uk.gov.hmrc.thirdpartydeveloperfrontend.config

import javax.inject.{Inject, Singleton}

import play.api.{ConfigLoader, Configuration}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

@Singleton
class ApplicationConfig @Inject() (config: Configuration) extends ServicesConfig(config) {
  def getConfigDefaulted[A](key: String, default: A)(implicit loader: ConfigLoader[A]): A = config.getOptional[A](key)(loader).getOrElse(default)

  val contactFormServiceIdentifier                     = "API"
  val betaFeedbackUrl                                  = "/contact/beta-feedback"
  val betaFeedbackUnauthenticatedUrl                   = "/contact/beta-feedback-unauthenticated"
  val thirdPartyDeveloperUrl: String                   = baseUrl("third-party-developer")
  val thirdPartyApplicationProductionUrl: String       = thirdPartyApplicationUrl("third-party-application-production")
  val thirdPartyApplicationProductionUseProxy: Boolean = useProxy("third-party-application-production")
  val thirdPartyApplicationSandboxUrl: String          = thirdPartyApplicationUrl("third-party-application-sandbox")
  val thirdPartyApplicationSandboxUseProxy: Boolean    = useProxy("third-party-application-sandbox")
  val thirdPartyApplicationProductionApiKey: String    = getConfString("third-party-application-production.api-key", "")
  val thirdPartyApplicationSandboxApiKey: String       = getConfString("third-party-application-sandbox.api-key", "")
  val deskproUrl: String                               = baseUrl("deskpro-ticket-queue")

  lazy val contactPath: String = getConfigDefaulted("contactPath", "")

  lazy val reportAProblemPartialUrl               = s"$contactPath/contact/problem_reports_ajax?service=$contactFormServiceIdentifier"
  lazy val reportAProblemNonJSUrl                 = s"$contactPath/contact/problem_reports_nonjs?service=$contactFormServiceIdentifier"
  lazy val apiDocumentationFrontendUrl: String    = buildUrl("platform.internal.frontend").getOrElse(baseUrl("api-documentation-frontend"))
  lazy val thirdPartyDeveloperFrontendUrl: String = buildUrl("platform.internal.frontend").getOrElse(baseUrl("third-party-developer-frontend"))
  lazy val productionApiBaseUrl: Option[String]   = buildUrl("platform.api.production")
  lazy val sandboxApiBaseUrl: Option[String]      = buildUrl("platform.api.sandbox")

  lazy val sessionTimeout: Int   = getInt("timeout.timeout")
  lazy val sessionCountdown: Int = getInt("timeout.countdown")

  lazy val sessionTimeoutInSeconds: Int   = getInt("session.timeoutSeconds")
  lazy val analyticsToken: Option[String] = config.getOptional[String]("google-analytics.token").filterNot(_ == "")
  lazy val analyticsHost: String          = getConfigDefaulted("google-analytics.host", "auto")
  lazy val securedCookie: Boolean         = getConfigDefaulted("cookie.secure", true)
  lazy val title                          = "HMRC Developer Hub"
  lazy val jsonEncryptionKey: String      = getString("json.encryption.key")
  lazy val hasSandbox: Boolean            = getConfigDefaulted("hasSandbox", false)
  lazy val retryCount: Int                = getConfigDefaulted("retryCount", 0)
  lazy val retryDelayMilliseconds: Int    = getConfigDefaulted("retryDelayMilliseconds", 500)

  lazy val nameOfPrincipalEnvironment: String   = getConfigDefaulted("features.nameOfPrincipalEnvironment", "Production")
  lazy val nameOfSubordinateEnvironment: String = getConfigDefaulted("features.nameOfSubordinateEnvironment", "Sandbox")

  lazy val platformFrontendHost: String = getConfigDefaulted("platform.frontend.host", "http://localhost:9685")

  lazy val reportProblemHost: String =
    config.underlying.getString("report-a-problem.base.url") + config.underlying.getString("urls.report-a-problem.problem")

  // API Subscription Fields
  val apiSubscriptionFieldsProductionUrl: String       = apiSubscriptionFieldsUrl("api-subscription-fields-production")
  val apiSubscriptionFieldsProductionApiKey: String    = getConfString("api-subscription-fields-production.api-key", "")
  val apiSubscriptionFieldsProductionUseProxy: Boolean = useProxy("api-subscription-fields-production")
  val apiSubscriptionFieldsSandboxUrl: String          = apiSubscriptionFieldsUrl("api-subscription-fields-sandbox")
  val apiSubscriptionFieldsSandboxUseProxy: Boolean    = useProxy("api-subscription-fields-sandbox")
  val apiSubscriptionFieldsSandboxApiKey: String       = getConfString("api-subscription-fields-sandbox.api-key", "")

  // PPNS
  val ppnsProductionUrl: String              = pushPullNotificationsApiUrl("push-pull-notifications-api-production")
  val ppnsProductionAuthorizationKey: String = getConfString("push-pull-notifications-api-production.authorizationKey", "")
  val ppnsSandboxUrl: String                 = pushPullNotificationsApiUrl("push-pull-notifications-api-sandbox")
  val ppnsSandboxUseProxy: Boolean           = useProxy("push-pull-notifications-api-sandbox")
  val ppnsSandboxApiKey: String              = getConfString("push-pull-notifications-api-sandbox.api-key", "")
  val ppnsSandboxAuthorizationKey: String    = getConfString("push-pull-notifications-api-sandbox.authorizationKey", "")

  private def buildUrl(key: String) = {
    (getConfigDefaulted(s"$key.protocol", ""), getConfigDefaulted(s"$key.host", "")) match {
      case (p, h) if !p.isEmpty && !h.isEmpty => Some(s"$p://$h")
      case (p, h) if p.isEmpty                => Some(s"https://$h")
      case _                                  => None
    }
  }

  private def serviceUrl(key: String)(serviceName: String): String = {
    if (useProxy(serviceName)) s"${baseUrl(serviceName)}/${getConfString(s"$serviceName.context", key)}"
    else baseUrl(serviceName)
  }

  private def apiSubscriptionFieldsUrl = serviceUrl("api-subscription-fields")(_)

  private def thirdPartyApplicationUrl = serviceUrl("third-party-application")(_)

  private def pushPullNotificationsApiUrl = serviceUrl("push-pull-notifications-api")(_)

  private def useProxy(serviceName: String) = getConfBool(s"$serviceName.use-proxy", false)
}
