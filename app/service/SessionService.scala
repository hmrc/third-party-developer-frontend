/*
 * Copyright 2018 HM Revenue & Customs
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
import domain.{Session, SessionInvalid, LoginRequest}

import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

trait SessionService {
  val thirdPartyDeveloperConnector: ThirdPartyDeveloperConnector

  def authenticate(emailAddress: String, password: String)(implicit hc: HeaderCarrier): Future[Session] =
    thirdPartyDeveloperConnector.createSession(LoginRequest(emailAddress, password))

  def fetch(sessionId: String)(implicit hc: HeaderCarrier): Future[Option[Session]] =
    thirdPartyDeveloperConnector.fetchSession(sessionId)
      .map(Some(_))
      .recover {
        case _: SessionInvalid => None
      }

  def destroy(sessionId: String)(implicit hc: HeaderCarrier): Future[Int] =
    thirdPartyDeveloperConnector.deleteSession(sessionId)
}

object SessionService extends SessionService {
  override val thirdPartyDeveloperConnector = ThirdPartyDeveloperConnector
}
