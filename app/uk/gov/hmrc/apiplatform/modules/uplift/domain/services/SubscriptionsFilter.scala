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

package uk.gov.hmrc.apiplatform.modules.uplift.domain.services

import uk.gov.hmrc.apiplatform.modules.uplift.domain.models.ApiSubscriptions
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.APISubscriptionStatus
import uk.gov.hmrc.apiplatform.modules.apis.domain.models._

object SubscriptionsFilter {

  def apply(upliftableApiIds: Set[ApiIdentifier], subscriptionsFromFlow: ApiSubscriptions): (APISubscriptionStatus) => Boolean = (s) => {
    upliftableApiIds.contains(s.apiIdentifier) && subscriptionsFromFlow.subscriptions.applyOrElse[ApiIdentifier, Boolean](s.apiIdentifier, _ => false)
  }
}
