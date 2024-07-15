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

import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{LaxEmailAddress, UserId}
import uk.gov.hmrc.apiplatform.modules.tpd.core.domain.models.User
import uk.gov.hmrc.apiplatform.modules.tpd.emailpreferences.domain.models.EmailPreferences
import uk.gov.hmrc.apiplatform.modules.tpd.mfa.domain.models.MfaDetail
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{CollaboratorTracker, UserIdTracker}
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import java.time.Instant


trait DeveloperBuilder extends CollaboratorTracker {
  self: UserIdTracker =>

  def buildDeveloperWithRandomId(
      emailAddress: LaxEmailAddress = "something@example.com".toLaxEmail,
      firstName: String = "John",
      lastName: String = "Doe",
      organisation: Option[String] = None,
      mfaDetails: List[MfaDetail] = List.empty,
      emailPreferences: EmailPreferences = EmailPreferences.noPreferences
    ) = {
    buildDeveloper(emailAddress, firstName, lastName, organisation, mfaDetails, emailPreferences).copy(userId = UserId.random)
  }

  def buildDeveloper(
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
      Instant.now,
      Instant.now,
      true,
     accountSetup =  None,
      organisation,
      mfaEnabled = true,
      mfaDetails,
      nonce = None,
      emailPreferences,
      idOf(emailAddress)
    )
  }
}
