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

package uk.gov.hmrc.apiplatform.modules.tpd.test.builders

import java.time.Instant
import java.time.temporal.ChronoUnit

import uk.gov.hmrc.apiplatform.modules.tpd.mfa.domain.models.{AuthenticatorAppMfaDetail, MfaId, SmsMfaDetail}

trait MfaDetailBuilder {
  val verifiedAuthenticatorAppMfaDetail = buildAuthenticatorAppMfaDetail(name = "Auth App", verified = true)
  val verifiedSmsMfaDetail              = buildSmsMfaDetail(name = "Text Message", mobileNumber = "0123456789", verified = true)

  def buildAuthenticatorAppMfaDetail(name: String, verified: Boolean, createdOn: Instant = Instant.now().truncatedTo(ChronoUnit.MILLIS)) = {
    AuthenticatorAppMfaDetail(MfaId.random, name, createdOn, verified)
  }

  def buildSmsMfaDetail(name: String, mobileNumber: String, verified: Boolean, createdOn: Instant = Instant.now().truncatedTo(ChronoUnit.MILLIS)) = {
    SmsMfaDetail(MfaId.random, name, createdOn, mobileNumber, verified)
  }
}
