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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers

import uk.gov.hmrc.apiplatform.modules.apis.domain.models._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.AsyncHmrcSpec

trait SubscriptionTestSugar {
  self: AsyncHmrcSpec =>

  def verifyApplicationSubscription(
      applicationSubscription: APISubscriptions,
      expectedApiHumanReadableAppName: String,
      expectedApiServiceName: String,
      expectedVersions: List[ApiVersion]
    ): Unit = {
    applicationSubscription.apiHumanReadableAppName shouldBe expectedApiHumanReadableAppName
    applicationSubscription.apiServiceName.value shouldBe expectedApiServiceName
    applicationSubscription.subscriptions.map(_.apiVersion) shouldBe expectedVersions
  }

}
