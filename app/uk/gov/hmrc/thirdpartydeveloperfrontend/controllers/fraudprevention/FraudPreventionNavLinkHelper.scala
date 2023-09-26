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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.fraudprevention

import uk.gov.hmrc.apiplatform.modules.common.domain.models.Environment
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.FraudPreventionConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.ApplicationRequest
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.APISubscriptionStatus
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Application
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.FraudPreventionNavLinkViewModel

trait FraudPreventionNavLinkHelper {

  def createFraudNavModel(fraudPreventionConfig: FraudPreventionConfig)(implicit request: ApplicationRequest[_]) = {
    createOptionalFraudPreventionNavLinkViewModel(request.application, request.subscriptions, fraudPreventionConfig)
  }

  def createOptionalFraudPreventionNavLinkViewModel(
      application: Application,
      subscriptions: List[APISubscriptionStatus],
      fraudPreventionConfig: FraudPreventionConfig
    ): Option[FraudPreventionNavLinkViewModel] = {
    if (fraudPreventionConfig.enabled) {
      val apis            = fraudPreventionConfig.apisWithFraudPrevention
      val isProduction    = application.deployedTo == Environment.PRODUCTION
      val shouldBeVisible = subscriptions.exists(x => apis.contains(x.serviceName) && x.subscribed && isProduction)
      Some(FraudPreventionNavLinkViewModel(shouldBeVisible, fraudPreventionConfig.uri))
    } else {
      None
    }

  }
}
