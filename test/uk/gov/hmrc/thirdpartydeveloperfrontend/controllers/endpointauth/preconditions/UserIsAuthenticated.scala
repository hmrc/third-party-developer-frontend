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

import play.api.http.Status.OK
import play.api.libs.crypto.CookieSigner
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.endpointauth.MockConnectors
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.{TicketCreated, UserAuthenticationResponse}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.{Developer, LoggedInState, Session, UserId}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.emailpreferences.EmailPreferences

import scala.concurrent.Future

trait UserIsAuthenticated extends MockConnectors {
  val sessionId = "my session"
  val user = Developer(
    UserId.random, "admin@example.com", "Bob", "Admin", None, List.empty, EmailPreferences.noPreferences
  )
  val session = Session(sessionId, user, LoggedInState.LOGGED_IN)

  when(tpdConnector.authenticate(*)(*)).thenReturn(Future.successful(UserAuthenticationResponse(false, false, None, Some(session))))
  when(tpdConnector.fetchSession(eqTo(sessionId))(*)).thenReturn(Future.successful(session))
  when(tpdConnector.deleteSession(eqTo(sessionId))(*)).thenReturn(Future.successful(OK))

}
