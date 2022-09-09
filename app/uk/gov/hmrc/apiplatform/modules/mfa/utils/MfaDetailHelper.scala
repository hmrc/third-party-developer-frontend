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

package uk.gov.hmrc.apiplatform.modules.mfa.utils

import uk.gov.hmrc.apiplatform.modules.mfa.models.{AuthenticatorAppMfaDetailSummary, MfaDetail, MfaType}

object MfaDetailHelper {

  def isAuthAppMfaVerified(mfaDetails: List[MfaDetail]): Boolean = {
    //TODO: This will be modified when the SMS authentication is added.
    mfaDetails
      .exists(x => x.mfaType == MfaType.AUTHENTICATOR_APP && x.verified)
  }

  def getAuthAppMfaVerified(mfaDetails: List[MfaDetail]): AuthenticatorAppMfaDetailSummary = {
    mfaDetails.filter(x => x.mfaType == MfaType.AUTHENTICATOR_APP && x.verified)
      .head.asInstanceOf[AuthenticatorAppMfaDetailSummary]
  }
}
