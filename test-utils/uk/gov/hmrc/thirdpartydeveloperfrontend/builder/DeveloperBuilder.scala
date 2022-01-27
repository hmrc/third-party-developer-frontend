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

import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.Developer
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.emailpreferences.EmailPreferences
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.CollaboratorTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.UserIdTracker

trait DeveloperBuilder extends CollaboratorTracker {
  self: UserIdTracker =>

  def buildDeveloper(
    emailAddress: String = "something@example.com",
    firstName: String = "John",
    lastName: String = "Doe",
    organisation: Option[String] = None,
    mfaEnabled: Option[Boolean] = None,
    emailPreferences: EmailPreferences = EmailPreferences.noPreferences
  ): Developer = {
    Developer(
      idOf(emailAddress),
      emailAddress,
      firstName,
      lastName,
      organisation,
      mfaEnabled,
      emailPreferences
    )
  }
}