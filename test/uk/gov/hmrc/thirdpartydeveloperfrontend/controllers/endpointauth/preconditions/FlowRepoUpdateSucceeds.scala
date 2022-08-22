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

import akka.stream.scaladsl.Flow
import play.api.http.Status.OK
import play.api.libs.json.OFormat
import uk.gov.hmrc.apiplatform.modules.uplift.domain.models.{ApiSubscriptions, GetProductionCredentialsFlow}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.endpointauth.MockConnectors
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.{ApiContext, ApiIdentifier, ApiVersion}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.{ApplicationId, SellResellOrDistribute, UpliftData}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.flows.FlowType.{GET_PRODUCTION_CREDENTIALS, IP_ALLOW_LIST}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.flows.IpAllowlistFlow

import scala.concurrent.Future

trait FlowRepoUpdateSucceeds extends MockConnectors {
  when(flowRepository.updateLastUpdated(*)).thenReturn(Future.successful())
  when(flowRepository.fetchBySessionIdAndFlowType(*[String], eqTo(GET_PRODUCTION_CREDENTIALS))(*[OFormat[GetProductionCredentialsFlow]])).thenReturn(Future.successful(Some(
    GetProductionCredentialsFlow("my session", Some(SellResellOrDistribute("sell")), Some(ApiSubscriptions(Map(ApiIdentifier(ApiContext("ctx"), ApiVersion("1.0")) -> true))))
  )))
  when(flowRepository.fetchBySessionIdAndFlowType(*[String], eqTo(IP_ALLOW_LIST))(*[OFormat[IpAllowlistFlow]])).thenReturn(Future.successful(Some(IpAllowlistFlow("my session", Set("1.2.3.4")))))
  when(flowRepository.deleteBySessionIdAndFlowType(*,*)).thenReturn(Future.successful(true))
  when(flowRepository.saveFlow(isA[GetProductionCredentialsFlow])(*[OFormat[GetProductionCredentialsFlow]])).thenReturn(Future.successful(GetProductionCredentialsFlow("my session", None, None)))
  when(flowRepository.saveFlow(isA[IpAllowlistFlow])(*[OFormat[IpAllowlistFlow]])).thenReturn(Future.successful(IpAllowlistFlow("my session", Set.empty)))
  when(apmConnector.fetchUpliftableSubscriptions(*[ApplicationId])(*)).thenReturn(Future.successful(Set(ApiIdentifier(ApiContext("ctx"), ApiVersion("1.0")))))
  when(apmConnector.upliftApplicationV2(*[ApplicationId], *[UpliftData])(*)).thenAnswer((appId: ApplicationId, _: UpliftData) => Future.successful(appId))
}
