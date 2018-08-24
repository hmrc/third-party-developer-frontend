/*
 * Copyright 2018 HM Revenue & Customs
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

package unit.controllers

import controllers.APISubscriptions
import domain.APIStatus._
import domain.{APIAccess, APIStatus, APISubscriptionStatus, APIVersion}
import uk.gov.hmrc.play.test.UnitSpec

trait SubscriptionTestHelperSugar {

  self: UnitSpec =>

  def subscriptionStatus(apiName: String, serviceName: String, context: String, version: String, status: APIStatus = STABLE, subscribed: Boolean = false, requiresTrust: Boolean = false, access: Option[APIAccess] = None, isTestSupport: Boolean = false) =
    APISubscriptionStatus(apiName, serviceName, context, APIVersion(version, status, access), subscribed, requiresTrust, isTestSupport = isTestSupport)

  val sampleSubscriptions: Seq[APISubscriptionStatus] = {
    Seq(
      subscriptionStatus("Individual Employment", "individual-employment", "individual-employment-context", "1.0", STABLE, subscribed = true),
      subscriptionStatus("Individual Employment", "individual-employment", "individual-employment-context", "2.0", BETA),
      subscriptionStatus("Individual Tax", "individual-tax", "individual-tax-context", "1.0", STABLE),
      subscriptionStatus("Individual Tax", "individual-tax", "individual-tax-context", "2.0", BETA)
    )
  }

  def verifyApplicationSubscription(applicationSubscription: APISubscriptions, expectedApiHumanReadableAppName: String, expectedApiServiceName: String, expectedVersions: Seq[APIVersion]) {
    applicationSubscription.apiHumanReadableAppName shouldBe expectedApiHumanReadableAppName
    applicationSubscription.apiServiceName shouldBe expectedApiServiceName
    applicationSubscription.subscriptions.map(_.apiVersion) shouldBe expectedVersions
  }
}
