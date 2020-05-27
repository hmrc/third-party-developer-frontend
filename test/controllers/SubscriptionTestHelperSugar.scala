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

package controllers

import cats.data.NonEmptyList
import domain._
import domain.APIStatus._
import domain.ApiSubscriptionFields.{SubscriptionFieldDefinition, SubscriptionFieldsWrapper, SubscriptionFieldValue}
import uk.gov.hmrc.play.test.UnitSpec

trait SubscriptionTestHelperSugar {

  self: UnitSpec =>

  def subscriptionStatus( apiName: String, serviceName: String, context: String, version: String,
                          status: APIStatus = STABLE, subscribed: Boolean = false, requiresTrust: Boolean = false, access: Option[APIAccess] = None, isTestSupport: Boolean = false,
                          fields: Option[SubscriptionFieldsWrapper] = None) =

    APISubscriptionStatus(apiName, serviceName, context, APIVersion(version, status, access), subscribed, requiresTrust, isTestSupport = isTestSupport, fields = fields)

  val sampleSubscriptions: Seq[APISubscriptionStatus] = {
    Seq(
      subscriptionStatus("Individual Employment", "individual-employment", "individual-employment-context", "1.0", STABLE, subscribed = true),
      subscriptionStatus("Individual Employment", "individual-employment", "individual-employment-context", "2.0", BETA),
      subscriptionStatus("Individual Tax", "individual-tax", "individual-tax-context", "1.0", STABLE),
      subscriptionStatus("Individual Tax", "individual-tax", "individual-tax-context", "2.0", BETA)
    )
  }

  def sampleSubscriptionsWithSubscriptionConfiguration(application: Application): Seq[APISubscriptionStatus] = {
    val sfd = SubscriptionFieldDefinition("name", "description", "short-description", "type", "hint")
    val sfv = SubscriptionFieldValue(sfd, "the value")

    val context = "individual-employment-context-2"
    val version = "1.0"
    val subscriptionFieldsWrapper = SubscriptionFieldsWrapper(application.id, application.clientId, context, version, NonEmptyList.one(sfv))

    Seq(
      subscriptionStatus("Individual Employment 2", "individual-employment-2", context, version, STABLE, subscribed = true, fields = Some(subscriptionFieldsWrapper))
    )
  }

  def verifyApplicationSubscription(applicationSubscription: APISubscriptions, expectedApiHumanReadableAppName: String, expectedApiServiceName: String, expectedVersions: Seq[APIVersion]) {
    applicationSubscription.apiHumanReadableAppName shouldBe expectedApiHumanReadableAppName
    applicationSubscription.apiServiceName shouldBe expectedApiServiceName
    applicationSubscription.subscriptions.map(_.apiVersion) shouldBe expectedVersions
  }

  def generateName(prefix: String) = s"$prefix-name"

  def generateField(prefix: String): SubscriptionFieldDefinition =
    SubscriptionFieldDefinition(
      name = generateName(prefix),
      description = s"$prefix-description",
      shortDescription = s"$prefix-short-description",
      hint = s"$prefix-hint",
      `type` = "STRING"
    )

  def generateValue(prefix: String) = s"$prefix-value"

  def generateValueName(prefix: String, index: Int) = s"$prefix-field-$index"

  def generateFieldValue(prefix: String, index: Int): SubscriptionFieldValue =
    SubscriptionFieldValue(
      definition = generateField(prefix),
      value = generateValueName(prefix, index)
    )

  val WHO_CARES = "who cares"

  def generateWrapper(prefix: String, count: Int): Option[SubscriptionFieldsWrapper] = {
    val rawFields = (1 to count).map(i => generateFieldValue(prefix, i)).toList
    val nelFields = NonEmptyList.fromList(rawFields)

    nelFields.map(fs =>
      SubscriptionFieldsWrapper(
        applicationId = WHO_CARES,
        clientId = WHO_CARES,
        apiContext = WHO_CARES,
        apiVersion = WHO_CARES,
        fields = fs
      )
    )
  }

  val onlyApiExampleMicroserviceSubscribedTo: APISubscriptionStatus =
    APISubscriptionStatus(
      name = "api-example-microservice",
      serviceName = "api-example-microservice",
      context = "example-api",
      apiVersion = APIVersion("1.0", APIStatus.STABLE),
      subscribed = true,
      requiresTrust = false,
      fields = None,
      isTestSupport = false
    )

  def exampleSubscriptionWithoutFields(prefix: String): APISubscriptionStatus =
    APISubscriptionStatus(
      name = generateName(prefix),
      serviceName = s"$prefix-api",
      context = s"/$prefix-api",
      apiVersion = APIVersion("1.0", APIStatus.STABLE),
      subscribed = true,
      requiresTrust = false,
      fields = None,
      isTestSupport = false
    )

  def exampleSubscriptionWithFields(prefix: String, count: Int): APISubscriptionStatus =
    exampleSubscriptionWithoutFields(prefix).copy(fields = generateWrapper(prefix, count))

}
