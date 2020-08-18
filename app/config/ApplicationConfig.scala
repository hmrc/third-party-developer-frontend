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

package config

import javax.inject.{Inject, Singleton}
import org.joda.time._
import play.api.{ConfigLoader, Configuration}
import service.MfaMandateService
import uk.gov.hmrc.play.bootstrap.config.{RunMode, ServicesConfig}

@Singleton
class ApplicationConfig @Inject()(config: Configuration, runMode: RunMode) extends ServicesConfig(config, runMode) {
  def getConfigDefaulted[A](key: String, default: A)(implicit loader: ConfigLoader[A]) = config.getOptional[A](key)(loader).getOrElse(default)

  val env = runMode.env

  val contactFormServiceIdentifier = "API"
  val betaFeedbackUrl = "/contact/beta-feedback"
  val betaFeedbackUnauthenticatedUrl = "/contact/beta-feedback-unauthenticated"
  val thirdPartyDeveloperUrl = baseUrl("third-party-developer")
  val thirdPartyApplicationProductionUrl = thirdPartyApplicationUrl("third-party-application-production")
  val thirdPartyApplicationProductionBearerToken = getConfString("third-party-application-production.bearer-token", "")
  val thirdPartyApplicationProductionUseProxy = useProxy("third-party-application-production")
  val thirdPartyApplicationSandboxUrl = thirdPartyApplicationUrl("third-party-application-sandbox")
  val thirdPartyApplicationSandboxBearerToken = getConfString("third-party-application-sandbox.bearer-token", "")
  val thirdPartyApplicationSandboxUseProxy = useProxy("third-party-application-sandbox")
  val thirdPartyApplicationProductionApiKey = getConfString("third-party-application-production.api-key", "")
  val thirdPartyApplicationSandboxApiKey = getConfString("third-party-application-sandbox.api-key", "")
  val deskproUrl = baseUrl("hmrc-deskpro")

  lazy val contactPath = getConfigDefaulted(s"$env.contactPath", "")

  lazy val reportAProblemPartialUrl = s"$contactPath/contact/problem_reports_ajax?service=$contactFormServiceIdentifier"
  lazy val reportAProblemNonJSUrl = s"$contactPath/contact/problem_reports_nonjs?service=$contactFormServiceIdentifier"
  lazy val apiDocumentationFrontendUrl = buildUrl("platform.frontend").getOrElse(baseUrl("api-documentation-frontend"))
  lazy val thirdPartyDeveloperFrontendUrl = buildUrl("platform.frontend").getOrElse(baseUrl("third-party-developer-frontend"))
  lazy val productionApiBaseUrl = buildUrl("platform.api.production")
  lazy val sandboxApiBaseUrl = buildUrl("platform.api.sandbox")

  lazy val sessionTimeoutInSeconds = getInt("session.timeoutSeconds")
  lazy val analyticsToken = config.getOptional[String](s"$env.google-analytics.token").filterNot(_ == "")
  lazy val analyticsHost = getConfigDefaulted(s"$env.google-analytics.host", "auto")
  lazy val securedCookie = getConfigDefaulted(s"$env.cookie.secure", true)
  lazy val title = "Developer Hub"
  lazy val jsonEncryptionKey = getString("json.encryption.key")
  lazy val hasSandbox = getConfigDefaulted("hasSandbox", false)
  lazy val currentTermsOfUseVersion = getConfigDefaulted("currentTermsOfUseVersion", "")
  lazy val currentTermsOfUseDate = DateTime.parse(getConfigDefaulted("currentTermsOfUseDate", ""))
  lazy val retryCount = getConfigDefaulted("retryCount", 0)
  lazy val retryDelayMilliseconds = getConfigDefaulted("retryDelayMilliseconds", 500)

  lazy val nameOfPrincipalEnvironment: String = getConfigDefaulted("features.nameOfPrincipalEnvironment", "Production")
  lazy val nameOfSubordinateEnvironment: String = getConfigDefaulted("features.nameOfSubordinateEnvironment", "Sandbox")

  lazy val dateOfAdminMfaMandate: Option[LocalDate] = {
    config.getOptional[String]("dateOfAdminMfaMandate") match {
      case Some(s) => MfaMandateService.parseLocalDate(s)
      case None => None
    }
  }

  lazy val subordinateIsSandbox = nameOfSubordinateEnvironment == "Sandbox"
  lazy val principalIsProduction = nameOfPrincipalEnvironment == "Production"

  // API Subscription Fields
  val apiSubscriptionFieldsProductionUrl = apiSubscriptionFieldsUrl("api-subscription-fields-production")
  val apiSubscriptionFieldsProductionBearerToken = getConfString("api-subscription-fields-production.bearer-token", "")
  val apiSubscriptionFieldsProductionApiKey = getConfString("api-subscription-fields-production.api-key", "")
  val apiSubscriptionFieldsProductionUseProxy = useProxy("api-subscription-fields-production")
  val apiSubscriptionFieldsSandboxUrl = apiSubscriptionFieldsUrl("api-subscription-fields-sandbox")
  val apiSubscriptionFieldsSandboxBearerToken = getConfString("api-subscription-fields-sandbox.bearer-token", "")
  val apiSubscriptionFieldsSandboxUseProxy = useProxy("api-subscription-fields-sandbox")
  val apiSubscriptionFieldsSandboxApiKey = getConfString("api-subscription-fields-sandbox.api-key", "")

  private def buildUrl(key: String) = {
    (getConfigDefaulted(s"$env.$key.protocol", ""), getConfigDefaulted(s"$env.$key.host", "")) match {
      case (p, h) if !p.isEmpty && !h.isEmpty => Some(s"$p://$h")
      case (p, h) if p.isEmpty => Some(s"https://$h")
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
}
