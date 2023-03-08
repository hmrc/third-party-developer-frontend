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

package uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions

import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.APIAccessType.{PRIVATE, PUBLIC}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.ApiSubscriptionFields.{SubscriptionFieldValue, SubscriptionFieldsWrapper}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.VersionSubscription
import uk.gov.hmrc.apiplatform.modules.apis.domain.models._
import scala.util.Try

object APIDefinition {
  private val nonNumericOrPeriodRegex = "[^\\d^.]*"
  private val fallback                = Array(1, 0, 0)

  private def versionSorter(v1: ApiVersionDefinition, v2: ApiVersionDefinition) = {
    val v1Parts = Try(v1.version.value.replaceAll(nonNumericOrPeriodRegex, "").split("\\.").map(_.toInt)).getOrElse(fallback)
    val v2Parts = Try(v2.version.value.replaceAll(nonNumericOrPeriodRegex, "").split("\\.").map(_.toInt)).getOrElse(fallback)
    val pairs   = v1Parts.zip(v2Parts)

    val firstUnequalPair = pairs.find { case (one, two) => one != two }
    firstUnequalPair.fold(v1.version.value.length > v2.version.value.length) { case (a, b) => a > b }
  }

  def descendingVersion(v1: VersionSubscription, v2: VersionSubscription) = {
    versionSorter(v1.version, v2.version)
  }
}

case class ApiVersionDefinition(version: ApiVersion, status: APIStatus, access: Option[APIAccess] = None) {
  val displayedStatus = status.displayedStatus

  val accessType = access.map(_.`type`).getOrElse(APIAccessType.PUBLIC)

  val displayedAccessType = accessType.toString().toLowerCase().capitalize
}

case class APIAccess(`type`: APIAccessType)

// TODO - 5090 - Add new open access class
case class APISubscriptionStatus(
    name: String,
    serviceName: String,
    context: ApiContext,
    apiVersion: ApiVersionDefinition,
    subscribed: Boolean,
    requiresTrust: Boolean,
    fields: SubscriptionFieldsWrapper,
    isTestSupport: Boolean = false
  ) extends HasApiIdentifier {

  def isPrivate: Boolean = {
    apiVersion.accessType match {
      case PRIVATE => true
      case PUBLIC  => false
    }
  }

  lazy val apiIdentifier = ApiIdentifier(context, apiVersion.version)
}

case class APISubscriptionStatusWithSubscriptionFields(name: String, context: ApiContext, apiVersion: ApiVersionDefinition, fields: SubscriptionFieldsWrapper)

case class APISubscriptionStatusWithWritableSubscriptionField(
    name: String,
    context: ApiContext,
    apiVersion: ApiVersionDefinition,
    subscriptionFieldValue: SubscriptionFieldValue,
    oldValues: SubscriptionFieldsWrapper
  )

object APISubscriptionStatusWithSubscriptionFields {

  def apply(fields: List[APISubscriptionStatus]): List[APISubscriptionStatusWithSubscriptionFields] = {

    def toAPISubscriptionStatusWithSubscriptionFields(apiSubscriptionStatus: APISubscriptionStatus): Option[APISubscriptionStatusWithSubscriptionFields] = {
      if (apiSubscriptionStatus.fields.fields.isEmpty) {
        None
      } else {
        Some(APISubscriptionStatusWithSubscriptionFields(apiSubscriptionStatus.name, apiSubscriptionStatus.context, apiSubscriptionStatus.apiVersion, apiSubscriptionStatus.fields))
      }
    }

    fields.flatMap(toAPISubscriptionStatusWithSubscriptionFields)
  }
}
