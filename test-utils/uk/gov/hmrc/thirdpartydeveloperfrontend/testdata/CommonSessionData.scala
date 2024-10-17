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

package uk.gov.hmrc.thirdpartydeveloperfrontend.testdata

import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.{LoggedInState, UserSessionId}
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.DeveloperSessionBuilder

object CommonSessionData extends DeveloperSessionBuilder {
  val admin  = CommonUserData.admin.loggedIn
  val dev    = CommonUserData.dev.loggedIn
  val altDev = CommonUserData.altDev.loggedIn

  val partLoggedIn = altDev.copy(loggedInState = LoggedInState.PART_LOGGED_IN_ENABLING_MFA, sessionId = UserSessionId.random)
}

trait CommonSessionFixtures extends CommonUserFixtures {
  val adminSession  = CommonSessionData.admin
  val devSession    = CommonSessionData.dev
  val altDevSession = CommonSessionData.altDev

  val partLoggedInSession = CommonSessionData.partLoggedIn
}
