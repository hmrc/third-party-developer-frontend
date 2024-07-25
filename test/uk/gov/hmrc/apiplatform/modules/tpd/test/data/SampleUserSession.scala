/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.apiplatform.modules.tpd.test.data

import uk.gov.hmrc.apiplatform.modules.tpd.core.domain.models.User
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.{LoggedInState, UserSession, UserSessionId}
import uk.gov.hmrc.apiplatform.modules.tpd.test.builders.UserBuilder

trait SampleUserSession {
  self: UserBuilder =>

  lazy val user: User = buildTrackedUser()
  lazy val sessionId  = UserSessionId.random

  val partLoggedInSessionId                 = UserSessionId.random
  lazy val partLoggedInSession: UserSession = UserSession(partLoggedInSessionId, LoggedInState.PART_LOGGED_IN_ENABLING_MFA, user)
  lazy val userSession: UserSession         = UserSession(sessionId, LoggedInState.LOGGED_IN, user)
}
