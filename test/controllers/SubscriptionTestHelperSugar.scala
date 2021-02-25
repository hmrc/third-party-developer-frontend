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

import builder._
import domain.models.apidefinitions._
import domain.models.apidefinitions.APIStatus._
import domain.models.applications.{Application, ApplicationId, ClientId}
import domain.models.subscriptions.ApiSubscriptionFields.{SubscriptionFieldDefinition, SubscriptionFieldsWrapper, SubscriptionFieldValue}
import utils.AsyncHmrcSpec
import domain.models.subscriptions.FieldValue
import domain.models.subscriptions.FieldName

trait SubscriptionTestHelperSugar extends SubscriptionsBuilder {

  self: AsyncHmrcSpec =>

  val appId = ApplicationId("myAppId")
  val clientId = ClientId("myClientId")
  val employmentContext = ApiContext("individual-employment-context")
  val taxContext = ApiContext("individual-tax-context")
  val versionOne = ApiVersion("1.0")
  val versionTwo = ApiVersion("2.0")
  val versionThree = ApiVersion("3.0")

  def subscriptionStatus(
      apiName: String,
      serviceName: String,
      context: ApiContext,
      version: ApiVersion,
      status: APIStatus = STABLE,
      subscribed: Boolean = false,
      requiresTrust: Boolean = false,
      access: Option[APIAccess] = None,
      isTestSupport: Boolean = false,
      fields: Option[SubscriptionFieldsWrapper] = None
  ) = {

    val mappedFields = fields.getOrElse(emptySubscriptionFieldsWrapper(appId, clientId, context, version))

    APISubscriptionStatus(
      apiName,
      serviceName,
      context,
      ApiVersionDefinition(version, status, access),
      subscribed,
      requiresTrust,
      isTestSupport = isTestSupport,
      fields = mappedFields
    )
  }

  val sampleSubscriptions: List[APISubscriptionStatus] = {
    List(
      subscriptionStatus("Individual Employment", "individual-employment", employmentContext, versionOne, STABLE, subscribed = true),
      subscriptionStatus("Individual Employment", "individual-employment", employmentContext, versionTwo, BETA),
      subscriptionStatus("Individual Tax", "individual-tax", taxContext, versionOne, STABLE),
      subscriptionStatus("Individual Tax", "individual-tax", taxContext, versionTwo, BETA)
    )
  }

  def sampleSubscriptionsWithSubscriptionConfiguration(application: Application): List[APISubscriptionStatus] = {
    val sfv = buildSubscriptionFieldValue("the value")

    val subscriptionFieldsWrapper = SubscriptionFieldsWrapper(application.id, application.clientId, employmentContext, versionOne, List(sfv))

    List(
      subscriptionStatus("Individual Employment 2", "individual-employment-2", employmentContext, versionOne, STABLE, subscribed = true, fields = Some(subscriptionFieldsWrapper))
    )
  }

  def verifyApplicationSubscription(
      applicationSubscription: APISubscriptions,
      expectedApiHumanReadableAppName: String,
      expectedApiServiceName: String,
      expectedVersions: List[ApiVersionDefinition]
  ) {
    applicationSubscription.apiHumanReadableAppName shouldBe expectedApiHumanReadableAppName
    applicationSubscription.apiServiceName shouldBe expectedApiServiceName
    applicationSubscription.subscriptions.map(_.apiVersion) shouldBe expectedVersions
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
      applicationId = ApplicationId(WHO_CARES),
      clientId = ClientId(WHO_CARES),
      apiContext = ApiContext(WHO_CARES),
      apiVersion = ApiVersion(WHO_CARES),
      fields = fields
    )
  }

  val onlyApiExampleMicroserviceSubscribedTo: APISubscriptionStatus = {
    val context = ApiContext("example-api")
    val version = ApiVersionDefinition(versionOne, APIStatus.STABLE)
    val emptyFields = emptySubscriptionFieldsWrapper(appId, clientId, context, version.version)

    APISubscriptionStatus(
      name = "api-example-microservice",
      serviceName = "api-example-microservice",
      context = context,
      apiVersion = version,
      subscribed = true,
      requiresTrust = false,
      fields = emptyFields,
      isTestSupport = false
    )
  }

  def exampleSubscriptionWithoutFields(prefix: String): APISubscriptionStatus = {
    val context = ApiContext(s"/$prefix-api")
    val version = ApiVersionDefinition(versionOne, APIStatus.STABLE)
    val emptyFields = emptySubscriptionFieldsWrapper(appId, clientId, context, version.version)

    val subscriptinFieldInxed = 1
    APISubscriptionStatus(
      name = generateName(prefix, subscriptinFieldInxed),
      serviceName = s"$prefix-api",
      context = context,
      apiVersion = version,
      subscribed = true,
      requiresTrust = false,
      fields = emptyFields,
      isTestSupport = false
    )
  }

  def exampleSubscriptionWithFields(prefix: String, count: Int): APISubscriptionStatus =
    exampleSubscriptionWithoutFields(prefix).copy(fields = generateWrapper(prefix, count))


  def asSubscriptions(in: Seq[APISubscriptionStatus]): Set[ApiIdentifier] = {
    in.filter(_.subscribed).map(ass => {
      ApiIdentifier(ass.context, ass.apiVersion.version)
    })
    .toSet
  }

  def asFields(in: SubscriptionFieldsWrapper): Map[FieldName, FieldValue] = {
    in.fields.map(sfv => sfv.definition.name -> sfv.value).toMap
  }

  def asFields(subscriptions: Seq[APISubscriptionStatus]): Map[ApiContext, Map[ApiVersion, Map[FieldName, FieldValue]]] = {
    import cats._
    import cats.implicits._
    type MapType = Map[ApiVersion, Map[FieldName, FieldValue]]

    // Shortcut combining as we know there will never be records for the same version for the same context
    implicit def monoidVersions: Monoid[MapType] =
      new Monoid[MapType] {

        override def combine(x: MapType, y: MapType): MapType = x ++ y

        override def empty: MapType = Map.empty
      }

    Monoid.combineAll(
      subscriptions.filter(_.subscribed).toList.map(s => Map(s.context -> Map(s.apiVersion.version -> asFields(s.fields))))
    )
  }

}
