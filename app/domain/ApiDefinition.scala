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

package domain

import domain.ApiSubscriptionFields.{SubscriptionField, SubscriptionFieldsWrapper}
import play.api.libs.json.Json

import scala.util.Try

case class APISubscription(name: String, serviceName: String, context: String, versions: Seq[VersionSubscription],
                           requiresTrust: Option[Boolean], isTestSupport: Boolean = false)

object APIDefinition {
  private val nonNumericOrPeriodRegex = "[^\\d^.]*"
  private val fallback = Array(1, 0, 0)

  private def versionSorter(v1: APIVersion, v2: APIVersion) = {
    val v1Parts = Try(v1.version.replaceAll(nonNumericOrPeriodRegex, "").split("\\.").map(_.toInt)).getOrElse(fallback)
    val v2Parts = Try(v2.version.replaceAll(nonNumericOrPeriodRegex, "").split("\\.").map(_.toInt)).getOrElse(fallback)
    val pairs = v1Parts.zip(v2Parts)

    val firstUnequalPair = pairs.find { case (one, two) => one != two }
    firstUnequalPair.fold(v1.version.length > v2.version.length) { case (a, b) => a > b }
  }

  def descendingVersion(v1: VersionSubscription, v2: VersionSubscription) = {
    versionSorter(v1.version, v2.version)
  }
}

case class VersionSubscription(version: APIVersion, subscribed: Boolean)

case class APIVersion(version: String, status: APIStatus, access: Option[APIAccess] = None) {
  val displayedStatus = {
    status match {
      case APIStatus.ALPHA => "Alpha"
      case APIStatus.BETA => "Beta"
      case APIStatus.STABLE => "Stable"
      case APIStatus.DEPRECATED => "Deprecated"
      case APIStatus.RETIRED => "Retired"
    }
  }

  val accessType = access.map(_.`type`).getOrElse(APIAccessType.PUBLIC)
}

case class APIAccess(`type`: APIAccessType)

case class APIIdentifier(context: String, version: String)

case class APISubscriptionStatus(name: String, serviceName: String, context: String,
                                 apiVersion: APIVersion, subscribed: Boolean, requiresTrust: Boolean,
                                 fields: Option[SubscriptionFieldsWrapper] = None, isTestSupport: Boolean = false) {
  def canUnsubscribe = {
    apiVersion.status != APIStatus.DEPRECATED
  }
}

object DefinitionFormats {
  implicit val formatAPIAccess = Json.format[APIAccess]
  implicit val formatAPIVersion = Json.format[APIVersion]
  implicit val formatVersionSubscription = Json.format[VersionSubscription]
  implicit val formatAPISubscription = Json.format[APISubscription]
  implicit val formatAPIIdentifier = Json.format[APIIdentifier]
  implicit val formatSubscriptionField = Json.format[SubscriptionField]
}
