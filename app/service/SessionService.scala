/*
 * Copyright 2021 HM Revenue & Customs
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

package service

import connectors.ThirdPartyDeveloperConnector
import domain.models.connectors.{LoginRequest, TotpAuthenticationRequest, UserAuthenticationResponse}
import domain.models.developers.{Session, SessionInvalid}
import javax.inject.{Inject, Singleton}
import repositories.FlowRepository
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SessionService @Inject()(val thirdPartyDeveloperConnector: ThirdPartyDeveloperConnector,
                               val mfaMandateService: MfaMandateService,
                               val flowRepository: FlowRepository)(implicit val ec: ExecutionContext) {
  def authenticate(emailAddress: String, password: String)(implicit hc: HeaderCarrier): Future[UserAuthenticationResponse] = {
    for {
      mfaMandatedForUser <- mfaMandateService.isMfaMandatedForUser(emailAddress)
      response <- thirdPartyDeveloperConnector.authenticate(LoginRequest(emailAddress, password, mfaMandatedForUser))
    } yield response
  }

  def authenticateTotp(emailAddress: String, totp: String, nonce: String)(implicit hc: HeaderCarrier): Future[Session] = {
    thirdPartyDeveloperConnector.authenticateTotp(TotpAuthenticationRequest(emailAddress, totp, nonce))
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
