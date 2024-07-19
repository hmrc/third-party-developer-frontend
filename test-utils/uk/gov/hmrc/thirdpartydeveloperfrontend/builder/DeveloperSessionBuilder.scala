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

package uk.gov.hmrc.thirdpartydeveloperfrontend.builder

import uk.gov.hmrc.apiplatform.modules.tpd.core.domain.models.User
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.{LoggedInState, UserSessionId}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.session.DeveloperSession

trait DeveloperSessionBuilder {

  implicit class DeveloperSyntax(developer: User) {
    def loggedIn: DeveloperSession                = buildDeveloperSession(LoggedInState.LOGGED_IN, developer)
    def partLoggedInEnablingMFA: DeveloperSession = buildDeveloperSession(LoggedInState.PART_LOGGED_IN_ENABLING_MFA, developer)
  }

  private def buildDeveloperSession(loggedInState: LoggedInState, developer: User): DeveloperSession = {

    val sessionId = UserSessionId.random

    DeveloperSession(
      loggedInState,
      sessionId,
      developer
    )
  }
}
