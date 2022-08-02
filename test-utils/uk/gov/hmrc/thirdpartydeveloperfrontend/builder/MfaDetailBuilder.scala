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

package uk.gov.hmrc.thirdpartydeveloperfrontend.builder

import uk.gov.hmrc.apiplatform.modules.mfa.models.{AuthenticatorAppMfaDetailSummary, MfaId}

import java.time.LocalDateTime
import java.util.UUID

trait MfaDetailBuilder {
  val verifiedAuthenticatorAppMfaDetails = buildAuthenticatorAppMfaDetails(name = "name", verified = true)


  def buildAuthenticatorAppMfaDetails(name: String,
                                      verified: Boolean,
                                      createdOn: LocalDateTime = LocalDateTime.now) ={
    AuthenticatorAppMfaDetailSummary(MfaId(UUID.randomUUID()), name, createdOn, verified)
  }
}
