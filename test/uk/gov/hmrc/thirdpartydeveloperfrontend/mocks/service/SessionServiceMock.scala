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

package uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service

import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.Session
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.SessionService

import scala.concurrent.Future.successful

trait SessionServiceMock extends MockitoSugar with ArgumentMatchersSugar {
  val sessionServiceMock = mock[SessionService]

  private def fetchSessionById(sessionId: String, returns: Option[Session]) =
    when(sessionServiceMock.fetch(eqTo(sessionId))(*)).thenReturn(successful(returns))

  def fetchSessionByIdReturns(sessionId: String, returns: Session) =
    fetchSessionById(sessionId, Some(returns))

  def fetchSessionByIdReturnsNone(sessionId: String) =
    fetchSessionById(sessionId, None)

  def updateUserFlowSessionsReturnsSuccessfully(sessionId: String) =
    when(sessionServiceMock.updateUserFlowSessions(sessionId)).thenReturn(successful(()))
}
