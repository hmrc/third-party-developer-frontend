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

import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.services.ClockNow
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.apiplatform.modules.tpd.core.domain.models.User
import uk.gov.hmrc.apiplatform.modules.tpd.emailpreferences.domain.models.EmailPreferences

object CommonUserData extends FixedClock {

  val altDevId = UserId.random

  val admin  = buildUser(CommonCollaboratorData.admin.userId, CommonEmailData.admin, "AdminFirstName", "AdminLastName")
  val dev    = buildUser(CommonCollaboratorData.dev.userId, CommonEmailData.dev, "DevFirstName", "DevLastName")
  val altDev = buildUser(altDevId, CommonEmailData.altDev, "AltDevFirstName", "AltDevLastName")

  def buildUser(
      userId: UserId,
      emailAddress: LaxEmailAddress,
      firstName: String,
      lastName: String
    ): User = {
    User(
      emailAddress,
      firstName,
      lastName,
      instant,
      instant,
      true,
      accountSetup = None,
      List.empty,
      nonce = None,
      EmailPreferences.noPreferences,
      userId
    )
  }
}

trait CommonUserFixtures extends CommonCollaboratorFixtures with ClockNow {
  val adminUser  = CommonUserData.admin
  val devUser    = CommonUserData.dev
  val altDevUser = CommonUserData.altDev
}
