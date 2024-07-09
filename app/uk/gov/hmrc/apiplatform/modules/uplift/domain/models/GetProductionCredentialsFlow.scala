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

package uk.gov.hmrc.apiplatform.modules.uplift.domain.models

import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models.SellResellOrDistribute
import uk.gov.hmrc.apiplatform.modules.tpd.sessions.domain.models.UserSessionId
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.flows.{Flow, FlowType}

case class GetProductionCredentialsFlow(
    val sessionId: UserSessionId,
    sellResellOrDistribute: Option[SellResellOrDistribute],
    apiSubscriptions: Option[ApiSubscriptions]
  ) extends Flow {

  type Type = UserSessionId
  val flowType: FlowType = FlowType.GET_PRODUCTION_CREDENTIALS
}

object GetProductionCredentialsFlow {
  import play.api.libs.json.{Json, OFormat}
  implicit val format: OFormat[GetProductionCredentialsFlow] = Json.format[GetProductionCredentialsFlow]

  def create(sessionId: UserSessionId): GetProductionCredentialsFlow = GetProductionCredentialsFlow(sessionId, None, None)
}
