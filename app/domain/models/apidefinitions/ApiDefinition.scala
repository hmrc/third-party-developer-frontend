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

package domain.models.apidefinitions

import domain.models.subscriptions.VersionSubscription
import domain.models.subscriptions.ApiSubscriptionFields.SubscriptionFieldsWrapper
import play.api.libs.json.Json

import scala.util.Try
import domain.models.subscriptions.ApiSubscriptionFields.SubscriptionFieldValue
import scala.util.Random
import domain.models.apidefinitions.APIAccessType.PRIVATE
import domain.models.apidefinitions.APIAccessType.PUBLIC

object APIDefinition {
  private val nonNumericOrPeriodRegex = "[^\\d^.]*"
  private val fallback = Array(1, 0, 0)

  private def versionSorter(v1: ApiVersionDefinition, v2: ApiVersionDefinition) = {
    val v1Parts = Try(v1.version.value.replaceAll(nonNumericOrPeriodRegex, "").split("\\.").map(_.toInt)).getOrElse(fallback)
    val v2Parts = Try(v2.version.value.replaceAll(nonNumericOrPeriodRegex, "").split("\\.").map(_.toInt)).getOrElse(fallback)
    val pairs = v1Parts.zip(v2Parts)

    val firstUnequalPair = pairs.find { case (one, two) => one != two }
    firstUnequalPair.fold(v1.version.value.length > v2.version.value.length) { case (a, b) => a > b }
  }

  def descendingVersion(v1: VersionSubscription, v2: VersionSubscription) = {
    versionSorter(v1.version, v2.version)
  }
}


case class ApiVersionDefinition(version: ApiVersion, status: APIStatus, access: Option[APIAccess] = None) {
  val displayedStatus = {
    status match {
      case APIStatus.ALPHA      => "Alpha"
      case APIStatus.BETA       => "Beta"
      case APIStatus.STABLE     => "Stable"
      case APIStatus.DEPRECATED => "Deprecated"
      case APIStatus.RETIRED    => "Retired"
    }
  }

  val accessType = access.map(_.`type`).getOrElse(APIAccessType.PUBLIC)

  val displayedAccessType = {
    val text = accessType.toString()
    text.take(1) + text.toLowerCase.takeRight(text.length()-1)
  }
}

case class APIAccess(`type`: APIAccessType)

case class ApiContext(value: String) extends AnyVal

object ApiContext {

  implicit val formatApiContext = Json.valueFormat[ApiContext]

  implicit val ordering: Ordering[ApiContext] = new Ordering[ApiContext] {
    override def compare(x: ApiContext, y: ApiContext): Int = x.value.compareTo(y.value)
  }

  def random = ApiContext(Random.alphanumeric.take(10).mkString)
}

case class ApiVersion(value: String) extends AnyVal

object ApiVersion {

  implicit val formatApiVersion = Json.valueFormat[ApiVersion]

  implicit val ordering: Ordering[ApiVersion] = new Ordering[ApiVersion] {
    override def compare(x: ApiVersion, y: ApiVersion): Int = x.value.compareTo(y.value)
  }

  def random = ApiVersion(Random.nextDouble().toString)
}

case class ApiIdentifier(context: ApiContext, version: ApiVersion)

case class APISubscriptionStatus(
    name: String,
    serviceName: String,
    context: ApiContext,
    apiVersion: ApiVersionDefinition,
    subscribed: Boolean,
    requiresTrust: Boolean,
    fields: SubscriptionFieldsWrapper,
    isTestSupport: Boolean = false
) {
  def isPrivate: Boolean = {
    apiVersion.accessType match {
      case PRIVATE => true
      case PUBLIC => false
    }
  }
}

case class APISubscriptionStatusWithSubscriptionFields(name: String, context: ApiContext, apiVersion: ApiVersionDefinition, fields: SubscriptionFieldsWrapper)

case class APISubscriptionStatusWithWritableSubscriptionField(name: String, context: ApiContext, apiVersion: ApiVersionDefinition, subscriptionFieldValue: SubscriptionFieldValue, oldValues: SubscriptionFieldsWrapper)

object APISubscriptionStatusWithSubscriptionFields {
  def apply(fields: Seq[APISubscriptionStatus]): Seq[APISubscriptionStatusWithSubscriptionFields] = {

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

