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

import uk.gov.hmrc.apiplatform.modules.mfa.models.MfaId
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ThirdPartyDeveloperConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.InvalidEmail
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.{AccessCodeAuthenticationRequest, LoginRequest, UserAuthenticationResponse}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.{Session, SessionInvalid, UserId}
import uk.gov.hmrc.thirdpartydeveloperfrontend.repositories.FlowRepository

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SessionService @Inject()(val thirdPartyDeveloperConnector: ThirdPartyDeveloperConnector,
                               val appsByTeamMember: AppsByTeamMemberService,
                               val flowRepository: FlowRepository)(implicit val ec: ExecutionContext) {
  def authenticate(emailAddress: String, password: String, deviceSessionId: Option[UUID])(implicit hc: HeaderCarrier): Future[(UserAuthenticationResponse, UserId)] = {
    for {
      coreUser <- thirdPartyDeveloperConnector.findUserId(emailAddress).map(_.getOrElse(throw new InvalidEmail))
      mfaMandatedForUser <- appsByTeamMember.fetchProductionSummariesByAdmin(coreUser.id).map(_.nonEmpty)
      response <- thirdPartyDeveloperConnector.authenticate(LoginRequest(emailAddress, password, mfaMandatedForUser, deviceSessionId))
    } yield (response, coreUser.id)
  }

  def authenticateAccessCode(emailAddress: String, accessCode: String, nonce: String, mfaId: MfaId)(implicit hc: HeaderCarrier): Future[Session] = {
    thirdPartyDeveloperConnector.authenticateMfaAccessCode(AccessCodeAuthenticationRequest(emailAddress, accessCode, nonce, mfaId))
  }

  def fetch(sessionId: String)(implicit hc: HeaderCarrier): Future[Option[Session]] =
    thirdPartyDeveloperConnector.fetchSession(sessionId)
      .map(Some(_))
      .recover {
        case _: SessionInvalid => None
      }

  def destroy(sessionId: String)(implicit hc: HeaderCarrier): Future[Int] =
    thirdPartyDeveloperConnector.deleteSession(sessionId)

  def updateUserFlowSessions(sessionId: String): Future[Unit] = {
    flowRepository.updateLastUpdated(sessionId)
  }
}
