/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.thirdpartydeveloperfrontend.service

import uk.gov.hmrc.thirdpartydeveloperfrontend.domain._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.{CollaboratorActor, SubscribeToApi, UnsubscribeFromApi}

import java.time.LocalDateTime
import scala.concurrent.Future.successful

class ApplicationServiceApiSubscriptionsSpec extends ApplicationServiceCommonSetup  {

  trait Setup extends CommonSetup {
    val actor = CollaboratorActor("dev@example.com")
    val context = ApiContext("api1")
    val version = ApiVersion("1.0")
    val apiIdentifier = ApiIdentifier(context, version)

    theProductionConnectorthenReturnTheApplication(productionApplicationId, productionApplication)
  }

  "Subscribe to API" should {
    "subscribe application to an API version" in new Setup {
      val subscribeToApi = SubscribeToApi(actor, apiIdentifier, LocalDateTime.now(clock))

      when(mockSubscriptionsService.subscribeToApi(productionApplicationId, subscribeToApi))
        .thenReturn(successful(ApplicationUpdateSuccessful))
      when(mockApmConnector.updateApplication(productionApplicationId, subscribeToApi))
        .thenReturn(successful(productionApplication))

      await(applicationService.subscribeToApi(productionApplication, actor, apiIdentifier)) shouldBe ApplicationUpdateSuccessful
    }
  }

  "Unsubscribe from API" should {
    "unsubscribe application from an API version" in new Setup {
      val unsubscribeFromApi = UnsubscribeFromApi(actor, apiIdentifier, LocalDateTime.now(clock))

      when(mockSubscriptionsService.unsubscribeFromApi(productionApplicationId, unsubscribeFromApi))
        .thenReturn(successful(ApplicationUpdateSuccessful))
      when(mockApmConnector.updateApplication(productionApplicationId, unsubscribeFromApi))
        .thenReturn(successful(productionApplication))

      await(applicationService.unsubscribeFromApi(productionApplication, actor, apiIdentifier)) shouldBe ApplicationUpdateSuccessful
    }
  }

}
