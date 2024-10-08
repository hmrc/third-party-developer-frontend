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

package utils

import java.time.Instant
import java.util.UUID

import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{LaxEmailAddress, UserId}
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.apiplatform.modules.tpd.core.domain.models.User
import uk.gov.hmrc.apiplatform.modules.tpd.emailpreferences.domain.models.EmailPreferences
import uk.gov.hmrc.apiplatform.modules.tpd.mfa.domain.models.{AuthenticatorAppMfaDetail, MfaDetail, MfaId, SmsMfaDetail}

trait ComponentTestDeveloperBuilder extends FixedClock {
  val staticUserId               = UserId(UUID.fromString("11edcde7-c619-4bc1-bb6a-84dc14ea25cd"))
  val authenticatorAppMfaDetails = AuthenticatorAppMfaDetail(MfaId(UUID.fromString("13eae037-7b76-4bfd-8f77-feebd0611ebb")), "name", instant, verified = true)
  val smsMfaDetails              = SmsMfaDetail(MfaId(UUID.fromString("6a3b98f1-a2c0-488b-bf0b-cfc86ccfe24d")), "name", instant, "+447890123456", verified = true)

  def buildUser(
      emailAddress: LaxEmailAddress = "something@example.com".toLaxEmail,
      firstName: String = "John",
      lastName: String = "Doe",
      mfaDetails: List[MfaDetail] = List.empty,
      emailPreferences: EmailPreferences = EmailPreferences.noPreferences
    ): User = {
    User(
      emailAddress,
      firstName,
      lastName,
      Instant.now(),
      Instant.now(),
      true,
      None,
      mfaDetails,
      None,
      emailPreferences,
      staticUserId
    )
  }
}
