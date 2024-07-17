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

package uk.gov.hmrc.apiplatform.modules.tpd.builder

import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{LaxEmailAddress, UserId}
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.apiplatform.modules.tpd.core.domain.models.User
import uk.gov.hmrc.apiplatform.modules.tpd.emailpreferences.domain.models.EmailPreferences
import uk.gov.hmrc.apiplatform.modules.tpd.mfa.domain.models.MfaDetail
import uk.gov.hmrc.apiplatform.modules.tpd.utils.UserIdTracker

trait UserBuilder extends FixedClock {
  self: UserIdTracker =>

  def buildUser(
      emailAddress: LaxEmailAddress = "something@example.com".toLaxEmail,
      firstName: String = "John",
      lastName: String = "Doe",
      organisation: Option[String] = None,
      mfaDetails: List[MfaDetail] = List.empty,
      emailPreferences: EmailPreferences = EmailPreferences.noPreferences
    ): User = {
    User(
      emailAddress,
      firstName,
      lastName,
      instant,
      instant,
      true,
      accountSetup = None,
      organisation,
      mfaDetails,
      nonce = None,
      emailPreferences,
      UserId.random
    )
  }

  /*
   * Builds a user than can be referred to by email or id in other places and it will all "hang"
   * together through any mocked fetching users etc.
   */
  def buildTrackedUser(
      emailAddress: LaxEmailAddress = "something@example.com".toLaxEmail,
      firstName: String = "John",
      lastName: String = "Doe",
      organisation: Option[String] = None,
      mfaDetails: List[MfaDetail] = List.empty,
      emailPreferences: EmailPreferences = EmailPreferences.noPreferences
    ): User = {
    User(
      emailAddress,
      firstName,
      lastName,
      instant,
      instant,
      true,
      accountSetup = None,
      organisation,
      mfaDetails,
      nonce = None,
      emailPreferences,
      idOf(emailAddress)
    )
  }
}
