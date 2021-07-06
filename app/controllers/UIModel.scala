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

package controllers

import controllers.APISubscriptions.subscriptionNumberLabel
import domain.models.apidefinitions.{AccessType, ApiContext, APISubscriptionStatus, ApiVersion}
import domain.models.apidefinitions.APIGroup._
import domain.models.applications._
import org.joda.time.DateTime
import play.api.libs.json.Json
import uk.gov.hmrc.http.NotFoundException

import scala.collection.SortedMap
import domain.models.subscriptions.ApiData

case class PageData(app: Application, subscriptions: Option[GroupedSubscriptions], openAccessApis: Map[ApiContext, ApiData])

trait ApplicationSummary {
  def id: ApplicationId
  def name: String
  def environment: Environment
  def role: CollaboratorRole
  def termsOfUseStatus: TermsOfUseStatus
  def state: State
  def lastAccess: DateTime
  def serverTokenUsed: Boolean
  def createdOn: DateTime
  def accessType: AccessType
}

case class ProductionApplicationSummary(
  id: ApplicationId,
  name: String,
  role: CollaboratorRole,
  termsOfUseStatus: TermsOfUseStatus,
  state: State,
  lastAccess: DateTime,
  serverTokenUsed: Boolean = false,
  createdOn: DateTime,
  accessType: AccessType
) extends ApplicationSummary {
  val environment = Environment.PRODUCTION
}

case class SandboxApplicationSummary(
  id: ApplicationId,
  name: String,
  role: CollaboratorRole,
  termsOfUseStatus: TermsOfUseStatus,
  state: State,
  lastAccess: DateTime,
  serverTokenUsed: Boolean = false,
  createdOn: DateTime,
  accessType: AccessType,
  isValidTargetForUplift: Boolean
) extends ApplicationSummary {
  val environment = Environment.SANDBOX
}

object SandboxApplicationSummary {
  def from(app: Application, email: String): SandboxApplicationSummary = {
    require(app.deployedTo.isSandbox, "SandboxApplicationSummary cannot be built from Production App")

    val role = app.role(email).getOrElse(throw new NotFoundException("Role not found"))

    SandboxApplicationSummary(
      app.id,
      app.name,
      role,
      app.termsOfUseStatus,
      app.state.name,
      app.lastAccess,
      app.lastAccessTokenUsage.isDefined,
      app.createdOn,
      app.access.accessType,
      false// TODO
    )
  }
}

object ProductionApplicationSummary {
  def from(app: Application, email: String): ProductionApplicationSummary = {
    require(app.deployedTo.isProduction, "ProductionApplicationSummary cannot be built from Sandbox App")

    val role = app.role(email).getOrElse(throw new NotFoundException("Role not found"))

    ProductionApplicationSummary(
      app.id,
      app.name,
      role,
      app.termsOfUseStatus,
      app.state.name,
      app.lastAccess,
      app.lastAccessTokenUsage.isDefined,
      app.createdOn,
      app.access.accessType        
    )
  }
}

case class GroupedSubscriptions(testApis: Seq[APISubscriptions], apis: Seq[APISubscriptions], exampleApi: Option[APISubscriptions] = None)

case class APISubscriptions(apiHumanReadableAppName: String, apiServiceName: String, apiContext: ApiContext, subscriptions: Seq[APISubscriptionStatus]) {

  lazy val subscriptionNumberText = subscriptionNumberLabel(subscriptions)

  def hasSubscriptions = subscriptions.count(_.subscribed) > 0

  def isExpanded = hasSubscriptions
}

object APISubscriptions {
  def groupSubscriptions(subscriptions: Seq[APISubscriptionStatus]): Option[GroupedSubscriptions] = {
    val EXAMPLE_API_NAME = "api-example-microservice"

    if (subscriptions.nonEmpty) {
      val subscriptionGroups = subscriptions.groupBy(_.isTestSupport)
      val testApis = subscriptionGroups.get(true).map(groupSubscriptionsByServiceName).getOrElse(Seq.empty).sortBy(_.apiHumanReadableAppName)
      val apis = subscriptionGroups.get(false).map(groupSubscriptionsByServiceName).getOrElse(Seq.empty).sortBy(_.apiHumanReadableAppName)
      val exampleApis = apis.find(sub => sub.apiServiceName == EXAMPLE_API_NAME)
      Some(GroupedSubscriptions(testApis, apis.filter(sub => sub.apiServiceName != EXAMPLE_API_NAME), exampleApis))
    } else {
      None
    }
  }

  private def groupSubscriptionsByServiceName(subscriptions: Seq[APISubscriptionStatus]): Seq[APISubscriptions] = {
    SortedMap(subscriptions.groupBy(_.serviceName).toSeq: _*) map {
      case (serviceName, subscriptionsForAPI) => new APISubscriptions(subscriptionsForAPI.head.name, serviceName, subscriptionsForAPI.head.context, subscriptionsForAPI)
    } toSeq
  }

  def subscriptionNumberLabel(subscriptions: Seq[APISubscriptionStatus]) = subscriptions.count(_.subscribed) match {
    case 1  => "1 subscription"
    case nr => s"$nr subscriptions"
  }
}

case class AjaxSubscriptionResponse(apiName: ApiContext, group: String, numberOfSubscriptionText: String)

object AjaxSubscriptionResponse {
  implicit val format = Json.format[AjaxSubscriptionResponse]

  def from(context: ApiContext, version: ApiVersion, subscriptions: Seq[APISubscriptionStatus]): AjaxSubscriptionResponse = {
    val versionAccessType = subscriptions
      .find(s => s.context == context && s.apiVersion.version == version)
      .map(_.apiVersion.accessType)
      .getOrElse(throw new IllegalStateException(s"subscription should exist for ${context.value} ${version.value}"))

    val group = subscriptions
      .find(s => s.context == context && s.apiVersion.version == version)
      .map(sub =>
        if (sub.context.value == "hello") EXAMPLE
        else if (sub.isTestSupport) TEST_API
        else API
      )
      .getOrElse(throw new IllegalStateException(s"subscription should exist for ${context.value} ${version.value}"))

    val apiSubscriptions = subscriptions.filter(s => s.context == context && s.apiVersion.accessType == versionAccessType)

    AjaxSubscriptionResponse(context, group.toString, subscriptionNumberLabel(apiSubscriptions))
  }
}
