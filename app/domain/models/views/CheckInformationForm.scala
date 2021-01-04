/*
 * Copyright 2021 HM Revenue & Customs
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

package domain.models.views

import domain.models.applications.CheckInformation

case class CheckInformationForm(apiSubscriptionsComplete: Boolean = false,
                                apiSubscriptionConfigurationsComplete: Boolean = false,
                                contactDetailsComplete: Boolean = false,
                                teamConfirmedComplete: Boolean = false,
                                confirmedNameComplete: Boolean = false,
                                providedPrivacyPolicyURLComplete: Boolean = false,
                                providedTermsAndConditionsURLComplete: Boolean = false,
                                termsOfUseAgreementComplete: Boolean = false)





object CheckInformationForm {
  def fromCheckInformation(checkInformation: CheckInformation) = {
    CheckInformationForm(
      confirmedNameComplete = checkInformation.confirmedName,
      apiSubscriptionsComplete = checkInformation.apiSubscriptionsConfirmed,
      apiSubscriptionConfigurationsComplete = checkInformation.apiSubscriptionConfigurationsConfirmed,
      contactDetailsComplete = checkInformation.contactDetails.isDefined,
      providedPrivacyPolicyURLComplete = checkInformation.providedPrivacyPolicyURL,
      providedTermsAndConditionsURLComplete = checkInformation.providedTermsAndConditionsURL,
      teamConfirmedComplete = checkInformation.teamConfirmed,
      termsOfUseAgreementComplete = checkInformation.termsOfUseAgreements.exists(terms => terms.version.nonEmpty)
    )
  }
}
