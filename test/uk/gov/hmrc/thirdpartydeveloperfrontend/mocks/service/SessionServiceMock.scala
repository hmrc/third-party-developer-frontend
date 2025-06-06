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

import scala.concurrent.Future.successful

import org.mockito.{ArgumentMatchersSugar, MockitoSugar}

import play.api.test.Helpers._

import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.{UserSession, UserSessionId}
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.SessionService

trait SessionServiceMock extends MockitoSugar with ArgumentMatchersSugar {
  val sessionServiceMock = mock[SessionService]

  private def fetchSessionById(sessionId: UserSessionId, returns: Option[UserSession]) =
    when(sessionServiceMock.fetch(eqTo(sessionId))(*)).thenReturn(successful(returns))

  def fetchSessionByIdReturns(sessionId: UserSessionId, returns: UserSession) =
    fetchSessionById(sessionId, Some(returns))

  def fetchSessionByIdReturnsNone(sessionId: UserSessionId) =
    fetchSessionById(sessionId, None)

  def fetchSessionByIdReturnsNone() =
    when(sessionServiceMock.fetch(*)(*)).thenReturn(successful(None))

  def updateUserFlowSessionsReturnsSuccessfully(sessionId: UserSessionId) =
    when(sessionServiceMock.updateUserFlowSessions(sessionId)).thenReturn(successful(()))

  object FetchSessionById {

    def succeedsWith(sessionId: UserSessionId, returns: UserSession) =
      when(sessionServiceMock.fetch(eqTo(sessionId))(*)).thenReturn(successful(Some(returns)))

    def returnsNoneForId(sessionId: UserSessionId) = when(sessionServiceMock.fetch(eqTo(sessionId))(*)).thenReturn(successful(None))

    def returnsNoneForAny() = when(sessionServiceMock.fetch(*)(*)).thenReturn(successful(None))
  }

  object UpdateUserFlowSessions {

    def succeedsWith(sessionId: UserSessionId) = {
      when(sessionServiceMock.updateUserFlowSessions(sessionId)).thenReturn(successful(()))
    }
  }

  object DestroySession {

    def succeedsWith(sessionId: UserSessionId) = {
      when(sessionServiceMock.destroy(eqTo(sessionId))(*))
        .thenReturn(successful(NO_CONTENT))
    }
  }
}
