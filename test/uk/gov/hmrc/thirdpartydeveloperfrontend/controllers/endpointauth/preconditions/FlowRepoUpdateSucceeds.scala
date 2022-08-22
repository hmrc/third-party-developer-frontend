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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.endpointauth.preconditions

import play.api.libs.json.OFormat
import uk.gov.hmrc.apiplatform.modules.uplift.domain.models.{ApiSubscriptions, GetProductionCredentialsFlow}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.endpointauth.MockConnectors
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.ApiIdentifier
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.SellResellOrDistribute
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.flows.FlowType.{GET_PRODUCTION_CREDENTIALS, IP_ALLOW_LIST}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.flows.IpAllowlistFlow

import scala.concurrent.Future

trait FlowRepoUpdateSucceeds extends MockConnectors with HasSession with HasApplicationData {
  when(flowRepository.updateLastUpdated(*)).thenReturn(Future.successful())
  when(flowRepository.fetchBySessionIdAndFlowType(*[String], eqTo(GET_PRODUCTION_CREDENTIALS))(*[OFormat[GetProductionCredentialsFlow]])).thenReturn(Future.successful(Some(
    GetProductionCredentialsFlow(sessionId, Some(SellResellOrDistribute("sell")), Some(ApiSubscriptions(Map(ApiIdentifier(apiContext, apiVersion) -> true))))
  )))
  when(flowRepository.fetchBySessionIdAndFlowType(*[String], eqTo(IP_ALLOW_LIST))(*[OFormat[IpAllowlistFlow]])).thenReturn(Future.successful(Some(IpAllowlistFlow(sessionId, Set("1.2.3.4")))))
  when(flowRepository.deleteBySessionIdAndFlowType(*,*)).thenReturn(Future.successful(true))
  when(flowRepository.saveFlow(isA[GetProductionCredentialsFlow])(*[OFormat[GetProductionCredentialsFlow]])).thenReturn(Future.successful(GetProductionCredentialsFlow(sessionId, None, None)))
  when(flowRepository.saveFlow(isA[IpAllowlistFlow])(*[OFormat[IpAllowlistFlow]])).thenReturn(Future.successful(IpAllowlistFlow(sessionId, Set.empty)))
}
