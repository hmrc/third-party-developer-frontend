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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.endpointauth.preconditions

import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.{CheckInformation, ContactDetails, TermsOfUseAgreement}

import java.time.LocalDateTime

trait HasCheckInformationOrNot {
  val checkInformation: Option[CheckInformation]
}
trait HasCheckInformation extends HasCheckInformationOrNot with HasUserData {
  val checkInformation = Some(CheckInformation(true, true, true, Some(ContactDetails(s"$userFirstName $userLastName", userEmail, "01611234567")), true, true, true,
    List(TermsOfUseAgreement(userEmail, LocalDateTime.now(), "1.0"))))
}
trait HasNoCheckInformation extends HasCheckInformationOrNot {
  val checkInformation = None
}