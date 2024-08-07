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

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.common.domain.models.{LaxEmailAddress, UserId}
import uk.gov.hmrc.apiplatform.modules.tpd.core.domain.models.SessionId
import uk.gov.hmrc.apiplatform.modules.tpd.mfa.domain.models.{DeviceSessionId, MfaId}
import uk.gov.hmrc.apiplatform.modules.tpd.mfa.dto.AccessCodeAuthenticationRequest
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.{SessionInvalid, UserSession, UserSessionId}
import uk.gov.hmrc.apiplatform.modules.tpd.session.dto._
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ThirdPartyDeveloperConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.InvalidEmail
import uk.gov.hmrc.thirdpartydeveloperfrontend.repositories.FlowRepository

@Singleton
class SessionService @Inject() (
    val thirdPartyDeveloperConnector: ThirdPartyDeveloperConnector,
    val appsByTeamMember: AppsByTeamMemberService,
    val flowRepository: FlowRepository
  )(implicit val ec: ExecutionContext
  ) {

  def authenticate(emailAddress: LaxEmailAddress, password: String, deviceSessionId: Option[DeviceSessionId])(implicit hc: HeaderCarrier)
      : Future[(UserAuthenticationResponse, UserId)] = {
    for {
      coreUser           <- thirdPartyDeveloperConnector.findUserId(emailAddress).map(_.getOrElse(throw new InvalidEmail))
      mfaMandatedForUser <- appsByTeamMember.fetchProductionSummariesByAdmin(coreUser.id).map(_.nonEmpty)
      response           <- thirdPartyDeveloperConnector.authenticate(SessionCreateWithDeviceRequest(emailAddress, password, Some(mfaMandatedForUser), deviceSessionId))
    } yield (response, coreUser.id)
  }

  def authenticateAccessCode(emailAddress: LaxEmailAddress, accessCode: String, nonce: String, mfaId: MfaId)(implicit hc: HeaderCarrier): Future[UserSession] = {
    thirdPartyDeveloperConnector.authenticateMfaAccessCode(AccessCodeAuthenticationRequest(emailAddress, accessCode, nonce, mfaId))
  }

  def fetch(sessionId: UserSessionId)(implicit hc: HeaderCarrier): Future[Option[UserSession]] =
    thirdPartyDeveloperConnector.fetchSession(sessionId)
      .map(Some(_))
      .recover {
        case _: SessionInvalid => None
      }

  def destroy(sessionId: UserSessionId)(implicit hc: HeaderCarrier): Future[Int] =
    thirdPartyDeveloperConnector.deleteSession(sessionId)

  def updateUserFlowSessions(sessionId: SessionId): Future[Unit] = {
    flowRepository.updateLastUpdated(sessionId)
  }
}
