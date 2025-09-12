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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.endpointauth

import org.mockito.{ArgumentMatchersSugar, MockitoSugar}

import uk.gov.hmrc.apiplatform.modules.mfa.connectors.ThirdPartyDeveloperMfaConnector
import uk.gov.hmrc.apiplatform.modules.submissions.connectors.ThirdPartyApplicationSubmissionsConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors._
import uk.gov.hmrc.thirdpartydeveloperfrontend.repositories.FlowRepository

trait MockConnectors extends MockitoSugar with ArgumentMatchersSugar {
  val tpoConnector: ThirdPartyOrchestratorConnector                                        = mock[ThirdPartyOrchestratorConnector]
  val tpdConnector: ThirdPartyDeveloperConnector                                           = mock[ThirdPartyDeveloperConnector]
  val tpaProductionConnector: ThirdPartyApplicationProductionConnector                     = mock[ThirdPartyApplicationProductionConnector]
  val tpaSandboxConnector: ThirdPartyApplicationSandboxConnector                           = mock[ThirdPartyApplicationSandboxConnector]
  val deskproConnector: DeskproConnector                                                   = mock[DeskproConnector]
  val apiPlatformDeskproConnector: ApiPlatformDeskproConnector                             = mock[ApiPlatformDeskproConnector]
  val flowRepository: FlowRepository                                                       = mock[FlowRepository]
  val apmConnector: ApmConnector                                                           = mock[ApmConnector]
  val sandboxPushPullNotificationsConnector: SandboxPushPullNotificationsConnector         = mock[SandboxPushPullNotificationsConnector]
  val productionPushPullNotificationsConnector: ProductionPushPullNotificationsConnector   = mock[ProductionPushPullNotificationsConnector]
  val thirdPartyApplicationSubmissionsConnector: ThirdPartyApplicationSubmissionsConnector = mock[ThirdPartyApplicationSubmissionsConnector]
  val thirdPartyDeveloperMfaConnector: ThirdPartyDeveloperMfaConnector                     = mock[ThirdPartyDeveloperMfaConnector]
}
