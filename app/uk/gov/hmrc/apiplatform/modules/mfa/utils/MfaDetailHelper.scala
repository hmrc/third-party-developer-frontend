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

import uk.gov.hmrc.apiplatform.modules.mfa.models.{AuthenticatorAppMfaDetailSummary, MfaDetail, MfaType, SmsMfaDetailSummary}

object MfaDetailHelper {

  def isAuthAppMfaVerified(mfaDetails: List[MfaDetail]): Boolean = {
    mfaDetails
      .exists(x => x.mfaType == MfaType.AUTHENTICATOR_APP && x.verified)
  }

  def isSmsMfaVerified(mfaDetails: List[MfaDetail]): Boolean = {
    mfaDetails
      .exists(x => x.mfaType == MfaType.SMS && x.verified)
  }

  def getAuthAppMfaVerified(mfaDetails: List[MfaDetail]): Option[AuthenticatorAppMfaDetailSummary] = {
    mfaDetails.find(x => x.mfaType == MfaType.AUTHENTICATOR_APP && x.verified).map(_.asInstanceOf[AuthenticatorAppMfaDetailSummary])
  }

  def getSmsMfaVerified(mfaDetails: List[MfaDetail]): Option[SmsMfaDetailSummary] = {
    mfaDetails.find(x => x.mfaType == MfaType.SMS && x.verified).map(_.asInstanceOf[SmsMfaDetailSummary])
  }

  def getMfaDetailByType(mfaType: MfaType, details: List[MfaDetail]): MfaDetail = {
    details.filter(_.mfaType == mfaType).head
  }

  def countAndHasSmsAndAuthApp(mfaDetails: List[MfaDetail]): (Int, Boolean) = {
    (numberOfVerifiedMfa(mfaDetails), hasVerifiedSmsAndAuthApp(mfaDetails))
  }

  def hasVerifiedSmsAndAuthApp(mfaDetails: List[MfaDetail]): Boolean = {
    isAuthAppMfaVerified(mfaDetails) && isSmsMfaVerified(mfaDetails)
  }

  def numberOfVerifiedMfa(mfaDetails: List[MfaDetail]): Int = {
    mfaDetails.count(x => x.verified)
  }
}
