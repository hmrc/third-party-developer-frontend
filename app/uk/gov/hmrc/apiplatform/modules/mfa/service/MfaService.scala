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

package uk.gov.hmrc.apiplatform.modules.mfa.service

import uk.gov.hmrc.apiplatform.modules.mfa.connectors.ThirdPartyDeveloperMfaConnector
import uk.gov.hmrc.apiplatform.modules.mfa.models.MfaId
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.UserId

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MfaService @Inject()(thirdPartyDeveloperMfaConnector: ThirdPartyDeveloperMfaConnector)(implicit val ec: ExecutionContext) {

  def enableMfa(userId: UserId, mfaId: MfaId, totpCode: String)(implicit hc: HeaderCarrier): Future[MfaResponse] = {
    thirdPartyDeveloperMfaConnector.verifyMfa(userId, mfaId, totpCode) flatMap {
      case true => successful(MfaResponse(true))
      case _ => successful(MfaResponse(false))
    }
  }

  def removeMfaById(userId: UserId, mfaId: MfaId, totpCode: String)(implicit hc: HeaderCarrier): Future[MfaResponse] = {
    thirdPartyDeveloperMfaConnector.verifyMfa(userId, mfaId, totpCode) flatMap {
      case true => thirdPartyDeveloperMfaConnector.removeMfaById(userId, mfaId).map(_ => MfaResponse(true))
      case _ => successful(MfaResponse(false))
    }
  }
}

case class MfaResponse(totpVerified: Boolean)
