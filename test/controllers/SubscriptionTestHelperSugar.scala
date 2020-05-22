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

import domain.APIStatus._
import domain.{Application, APIAccess, APIStatus, APISubscriptionStatus, APIVersion}
import uk.gov.hmrc.play.test.UnitSpec
import domain.ApiSubscriptionFields.SubscriptionFieldsWrapper
import cats.data.NonEmptyList
import domain.ApiSubscriptionFields.SubscriptionFieldValue
import domain.ApiSubscriptionFields.SubscriptionFieldDefinition

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
}
