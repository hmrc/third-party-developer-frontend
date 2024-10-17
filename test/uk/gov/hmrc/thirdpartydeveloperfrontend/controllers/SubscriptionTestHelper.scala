/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers

import uk.gov.hmrc.apiplatform.modules.apis.domain.models._
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models._
import uk.gov.hmrc.apiplatform.modules.applications.subscriptions.domain.models.{FieldName, FieldValue}
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.ApiSubscriptionFields.{SubscriptionFieldDefinition, SubscriptionFieldValue, SubscriptionFieldsWrapper}

trait ExtendedSubscriptionTestHelper extends SubscriptionTestHelper {
  self: SampleApplication =>

  def subscriptionStatusEX(
      apiName: String,
      serviceName: String,
      context: ApiContext,
      version: ApiVersionNbr,
      status: ApiStatus = ApiStatus.STABLE,
      subscribed: Boolean = false,
      requiresTrust: Boolean = false,
      access: ApiAccess = ApiAccess.PUBLIC,
      isTestSupport: Boolean = false,
      fields: Option[SubscriptionFieldsWrapper] = None
    ) = super.subscriptionStatus(
    appId,
    clientId,
    apiName,
    serviceName,
    context,
    version,
    status,
    subscribed,
    requiresTrust,
    access,
    isTestSupport,
    fields
  )

  val sampleSubscriptions: List[APISubscriptionStatus] = super.sampleSubscriptions(appId, clientId)

  val onlyApiExampleMicroserviceSubscribedTo: APISubscriptionStatus = super.onlyApiExampleMicroserviceSubscribedTo(appId, clientId)

  def exampleSubscriptionWithoutFields(prefix: String): APISubscriptionStatus = super.exampleSubscriptionWithoutFields(appId, clientId)(prefix)

  def exampleSubscriptionWithFields(prefix: String, count: Int): APISubscriptionStatus = super.exampleSubscriptionWithFields(appId, clientId)(prefix, count)

}

trait SubscriptionTestHelper extends SubscriptionsBuilder {

  val employmentContext = ApiContext("individual-employment-context")
  val taxContext        = ApiContext("individual-tax-context")
  val versionOne        = ApiVersionNbr("1.0")
  val versionTwo        = ApiVersionNbr("2.0")
  val versionThree      = ApiVersionNbr("3.0")

  def subscriptionStatus(
      appId: ApplicationId,
      clientId: ClientId,
      apiName: String,
      serviceName: String,
      context: ApiContext,
      version: ApiVersionNbr,
      status: ApiStatus = ApiStatus.STABLE,
      subscribed: Boolean = false,
      requiresTrust: Boolean = false,
      access: ApiAccess = ApiAccess.PUBLIC,
      isTestSupport: Boolean = false,
      fields: Option[SubscriptionFieldsWrapper] = None
    ) = {

    val mappedFields = fields.getOrElse(emptySubscriptionFieldsWrapper(appId, clientId, context, version))

    APISubscriptionStatus(
      apiName,
      ServiceName(serviceName),
      context,
      ApiVersion(version, status, access, List.empty),
      subscribed,
      requiresTrust,
      isTestSupport = isTestSupport,
      fields = mappedFields
    )
  }

  def sampleSubscriptions(appId: ApplicationId, clientId: ClientId): List[APISubscriptionStatus] = {
    List(
      subscriptionStatus(appId, clientId, "Individual Employment", "individual-employment", employmentContext, versionOne, ApiStatus.STABLE, subscribed = true),
      subscriptionStatus(appId, clientId, "Individual Employment", "individual-employment", employmentContext, versionTwo, ApiStatus.BETA),
      subscriptionStatus(appId, clientId, "Individual Tax", "individual-tax", taxContext, versionOne, ApiStatus.STABLE),
      subscriptionStatus(appId, clientId, "Individual Tax", "individual-tax", taxContext, versionTwo, ApiStatus.BETA)
    )
  }

  def sampleSubscriptionsWithSubscriptionConfiguration(application: ApplicationWithCollaborators): List[APISubscriptionStatus] = {
    val sfv = buildSubscriptionFieldValue("the value")

    val subscriptionFieldsWrapper = SubscriptionFieldsWrapper(application.id, application.clientId, employmentContext, versionOne, List(sfv))

    List(
      subscriptionStatus(
        application.id,
        application.clientId,
        "Individual Employment 2",
        "individual-employment-2",
        employmentContext,
        versionOne,
        ApiStatus.STABLE,
        subscribed = true,
        fields = Some(subscriptionFieldsWrapper)
      )
    )
  }

  def generateName(prefix: String, index: Int = 1) = s"$prefix-name-$index"

  def generateField(prefix: String, index: Int): SubscriptionFieldDefinition =
    buildSubscriptionFieldValue(name = generateName(prefix, index)).definition

  def generateValue(prefix: String) = s"$prefix-value"

  def generateValueName(prefix: String, index: Int) = s"$prefix-field-$index"

  def generateFieldValue(prefix: String, index: Int): SubscriptionFieldValue =
    SubscriptionFieldValue(
      definition = generateField(prefix, index),
      value = FieldValue(generateValueName(prefix, index))
    )

  val WHO_CARES = "who cares"

  def generateWrapper(prefix: String, count: Int): SubscriptionFieldsWrapper = {
    val fields = (1 to count).map(i => generateFieldValue(prefix, i)).toList

    SubscriptionFieldsWrapper(
      applicationId = ApplicationId.random,
      clientId = ClientId(WHO_CARES),
      apiContext = ApiContext(WHO_CARES),
      apiVersion = ApiVersionNbr(WHO_CARES),
      fields = fields
    )
  }

  def onlyApiExampleMicroserviceSubscribedTo(appId: ApplicationId, clientId: ClientId): APISubscriptionStatus = {
    val context     = ApiContext("example-api")
    val version     = ApiVersion(versionOne, ApiStatus.STABLE, ApiAccess.PUBLIC, List.empty)
    val emptyFields = emptySubscriptionFieldsWrapper(appId, clientId, context, version.versionNbr)

    APISubscriptionStatus(
      name = "api-example-microservice",
      serviceName = ServiceName("api-example-microservice"),
      context = context,
      apiVersion = version,
      subscribed = true,
      requiresTrust = false,
      fields = emptyFields,
      isTestSupport = false
    )
  }

  def exampleSubscriptionWithoutFields(appId: ApplicationId, clientId: ClientId)(prefix: String): APISubscriptionStatus = {
    val context     = ApiContext(s"/$prefix-api")
    val version     = ApiVersion(versionOne, ApiStatus.STABLE, ApiAccess.PUBLIC, List.empty)
    val emptyFields = emptySubscriptionFieldsWrapper(appId, clientId, context, version.versionNbr)

    val subscriptinFieldInxed = 1
    APISubscriptionStatus(
      name = generateName(prefix, subscriptinFieldInxed),
      serviceName = ServiceName(s"$prefix-api"),
      context = context,
      apiVersion = version,
      subscribed = true,
      requiresTrust = false,
      fields = emptyFields,
      isTestSupport = false
    )
  }

  def exampleSubscriptionWithFields(appId: ApplicationId, clientId: ClientId)(prefix: String, count: Int): APISubscriptionStatus =
    exampleSubscriptionWithoutFields(appId, clientId)(prefix).copy(fields = generateWrapper(prefix, count))

  def asSubscriptions(in: List[APISubscriptionStatus]): Set[ApiIdentifier] = {
    in.filter(_.subscribed).map(ass => {
      ApiIdentifier(ass.context, ass.apiVersion.versionNbr)
    })
      .toSet
  }

  def asFields(in: SubscriptionFieldsWrapper): Map[FieldName, FieldValue] = {
    in.fields.map(sfv => sfv.definition.name -> sfv.value).toMap
  }

  def asFields(subscriptions: Seq[APISubscriptionStatus]): Map[ApiContext, Map[ApiVersionNbr, Map[FieldName, FieldValue]]] = {
    import cats._
    import cats.implicits._
    type MapType = Map[ApiVersionNbr, Map[FieldName, FieldValue]]

    // Shortcut combining as we know there will never be records for the same version for the same context
    implicit def monoidVersions: Monoid[MapType] =
      new Monoid[MapType] {

        override def combine(x: MapType, y: MapType): MapType = x ++ y

        override def empty: MapType = Map.empty
      }

    Monoid.combineAll(
      subscriptions.filter(_.subscribed).toList.map(s => Map(s.context -> Map(s.apiVersion.versionNbr -> asFields(s.fields))))
    )
  }

}
