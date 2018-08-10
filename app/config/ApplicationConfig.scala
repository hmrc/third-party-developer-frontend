/*
 * Copyright 2018 HM Revenue & Customs
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

package config

import org.joda.time.DateTime
import play.api.Configuration
import play.api.Play._
import uk.gov.hmrc.play.config.ServicesConfig

trait ApplicationConfig extends ServicesConfig {

  val configuration: Configuration

  val contactFormServiceIdentifier = "API"
  val betaFeedbackUrl = "/contact/beta-feedback"
  val betaFeedbackUnauthenticatedUrl = "/contact/beta-feedback-unauthenticated"
  val thirdPartyDeveloperUrl = baseUrl("third-party-developer")
  val thirdPartyApplicationProductionUrl = thirdPartyApplicationUrl("third-party-application-production")
  val thirdPartyApplicationProductionBearerToken = bearerToken("third-party-application-production")
  val thirdPartyApplicationProductionUseProxy = useProxy("third-party-application-production")
  val thirdPartyApplicationSandboxUrl = thirdPartyApplicationUrl("third-party-application-sandbox")
  val thirdPartyApplicationSandboxBearerToken = bearerToken("third-party-application-sandbox")
  val thirdPartyApplicationSandboxUseProxy = useProxy("third-party-application-sandbox")
  val deskproUrl = baseUrl("hmrc-deskpro")
  lazy val hotjarId = configuration.getInt(s"$env.hotjar.id").getOrElse(0)
  lazy val hotjarEnabled = configuration.getBoolean(s"$env.features.hotjar").getOrElse(false)
  lazy val contactPath = configuration.getString(s"$env.contactPath").getOrElse("")
  lazy val reportAProblemPartialUrl = s"$contactPath/contact/problem_reports_ajax?service=$contactFormServiceIdentifier"
  lazy val reportAProblemNonJSUrl = s"$contactPath/contact/problem_reports_nonjs?service=$contactFormServiceIdentifier"
  lazy val apiDocumentationFrontendUrl = buildUrl("platform.frontend").getOrElse(baseUrl("api-documentation-frontend"))
  lazy val thirdPartyDeveloperFrontendUrl = buildUrl("platform.frontend").getOrElse(baseUrl("third-party-developer-frontend"))
  lazy val productionApiBaseUrl = buildUrl("platform.api.production")
  lazy val sandboxApiBaseUrl = buildUrl("platform.api.sandbox")
  lazy val sessionTimeoutInSeconds= getConfig("session-timeout-in-seconds", configuration.getInt)
  lazy val analyticsToken = configuration.getString(s"$env.google-analytics.token")
  lazy val analyticsHost = configuration.getString(s"$env.google-analytics.host").getOrElse("auto")
  lazy val securedCookie = configuration.getBoolean(s"$env.cookie.secure").getOrElse(true)
  lazy val isExternalTestEnvironment= configuration.getBoolean("isExternalTestEnvironment").getOrElse(false)
  lazy val title = if (isExternalTestEnvironment) "Developer Sandbox"  else "Developer Hub"
  lazy val jsonEncryptionKey = getConfig("json.encryption.key")
  lazy val strategicSandboxEnabled = configuration.getBoolean("strategicSandboxEnabled").getOrElse(false)
  lazy val currentTermsOfUseVersion = configuration.getString("currentTermsOfUseVersion").getOrElse("")
  lazy val currentTermsOfUseDate = DateTime.parse(configuration.getString("currentTermsOfUseDate").getOrElse(""))

  // API Subscription Fields
  val apiSubscriptionFieldsProductionUrl = apiSubscriptionFieldsUrl("api-subscription-fields-production")
  val apiSubscriptionFieldsProductionBearerToken = bearerToken("api-subscription-fields-production")
  val apiSubscriptionFieldsProductionUseProxy = useProxy("api-subscription-fields-production")
  val apiSubscriptionFieldsSandboxUrl = apiSubscriptionFieldsUrl("api-subscription-fields-sandbox")
  val apiSubscriptionFieldsSandboxBearerToken = bearerToken("api-subscription-fields-sandbox")
  val apiSubscriptionFieldsSandboxUseProxy = useProxy("api-subscription-fields-sandbox")

  private def getConfig(key: String) =
    configuration.getString(key).getOrElse { sys.error(s"[$key] is not configured!") }

  private def getConfig[T](key: String, block: String => Option[T]) =
    block(key).getOrElse { sys.error(s"[$key] is not configured!") }

  private def buildUrl(key: String) = {
    (configuration.getString(s"$env.$key.protocol"), configuration.getString(s"$env.$key.host")) match {
      case (Some(protocol), Some(host)) => Some(s"$protocol://$host")
      case (None, Some(host)) => Some(s"https://$host")
      case _ => None
    }
  }

  private def serviceUrl(key: String)(serviceName: String): String = {
    if (useProxy(serviceName)) s"${baseUrl(serviceName)}/${getConfString(s"$serviceName.context", key)}"
    else baseUrl(serviceName)
  }

  private def apiSubscriptionFieldsUrl = serviceUrl("api-subscription-fields")(_)

  private def thirdPartyApplicationUrl = serviceUrl("third-party-application")(_)

  private def useProxy(serviceName: String) = getConfBool(s"$serviceName.use-proxy", false)

  private def bearerToken(serviceName: String) = getConfString(s"$serviceName.bearer-token", "")
}

object ApplicationConfig extends ApplicationConfig {
  override val configuration = play.api.Play.configuration
}
