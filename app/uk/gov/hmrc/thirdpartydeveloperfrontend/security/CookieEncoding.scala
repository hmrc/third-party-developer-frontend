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

package uk.gov.hmrc.thirdpartydeveloperfrontend.security

import java.security.MessageDigest

import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Cookie, RequestHeader}

import uk.gov.hmrc.apiplatform.modules.tpd.core.domain.models.SessionId
import uk.gov.hmrc.apiplatform.modules.tpd.mfa.domain.models.DeviceSessionId
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.UserSessionId
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig

trait CookieEncoding {
  implicit val appConfig: ApplicationConfig

  private[security] lazy val cookieName                         = "PLAY2AUTH_SESS_ID"
  private[security] lazy val cookieSecureOption: Boolean        = appConfig.securedCookie
  private[security] lazy val cookieHttpOnlyOption: Boolean      = true
  private[security] lazy val cookieDomainOption: Option[String] = None
  private[security] lazy val cookiePathOption: String           = "/"
  private[security] lazy val cookieMaxAge                       = Some(86400) // Hardcoded to 24 Hours until we fix the timeout dialog.

  private[security] lazy val devicecookieName   = "DEVICE_SESS_ID"
  private[security] lazy val devicecookieMaxAge = Some(604800) // Hardcoded to 7 Days

  val cookieSigner: CookieSigner

  def createUserCookie(sessionId: SessionId): Cookie = {
    Cookie(
      cookieName,
      encodeCookie(sessionId.toString()),
      cookieMaxAge,
      cookiePathOption,
      cookieDomainOption,
      cookieSecureOption,
      cookieHttpOnlyOption
    )
  }

  def createDeviceCookie(deviceSessionId: String): Cookie = {
    Cookie(
      devicecookieName,
      encodeCookie(deviceSessionId),
      devicecookieMaxAge,
      cookiePathOption,
      cookieDomainOption,
      cookieSecureOption,
      cookieHttpOnlyOption
    )
  }

  def encodeCookie(token: String): String = {
    cookieSigner.sign(token) + token
  }

  def extractUserSessionIdFromCookie(request: RequestHeader): Option[UserSessionId]     = decodeCookie(request, cookieName).flatMap(UserSessionId.apply)
  def extractDeviceSessionIdFromCookie(request: RequestHeader): Option[DeviceSessionId] = decodeCookie(request, devicecookieName).flatMap(DeviceSessionId.apply)

  private def decodeCookie(request: RequestHeader, theCookieName: String): Option[String] = {
    for {
      cookie       <- request.cookies.get(theCookieName)
      decodedValue <- decodeCookieValue(cookie.value)
    } yield decodedValue
  }

  private def decodeCookieValue(token: String): Option[String] = {
    val (hmac, value) = token.splitAt(40)

    val signedValue = cookieSigner.sign(value)

    if (MessageDigest.isEqual(signedValue.getBytes, hmac.getBytes)) {
      Some(value)
    } else {
      None
    }
  }
}
