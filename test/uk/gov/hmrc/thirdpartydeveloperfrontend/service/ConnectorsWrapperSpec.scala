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

package uk.gov.hmrc.thirdpartydeveloperfrontend.service

import java.time.Period
import scala.concurrent.ExecutionContext.Implicits.global

import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.ApplicationWithCollaboratorsFixtures
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{ApplicationId, ClientId}
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors._
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.PushPullNotificationsService.PushPullNotificationsConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.AsyncHmrcSpec

class ConnectorsWrapperSpec extends AsyncHmrcSpec with FixedClock with ApplicationWithCollaboratorsFixtures {

  val mockAppConfig = mock[ApplicationConfig]

  trait Setup {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val connectors = new ConnectorsWrapper(
      mock[ThirdPartyApplicationSandboxConnector],
      mock[ThirdPartyApplicationProductionConnector],
      mock[PushPullNotificationsConnector],
      mock[PushPullNotificationsConnector],
      mockAppConfig
    )
  }

  val productionApplicationId = ApplicationId.random
  val productionClientId      = ClientId("hBnFo14C0y4SckYUbcoL2PbFA40a")
  val grantLength             = Period.ofDays(547)

  val productionApplication = standardApp
  val sandboxApplicationId  = ApplicationId.random
  val sandboxClientId       = ClientId("Client ID")

  val sandboxApplication = standardApp.inSandbox().withId(sandboxApplicationId)

z}
