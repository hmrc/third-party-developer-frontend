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

package utils

import java.util.UUID

import domain.models.developers.{Developer, LoggedInState}
import domain.models.emailpreferences.EmailPreferences
import domain.models.developers.UserId

object DeveloperSession {

  def apply(email: String,
            firstName: String,
            lastName: String,
            organisation: Option[String] = None,
            mfaEnabled: Option[Boolean] = None,
            loggedInState: LoggedInState,
            emailPreferences: EmailPreferences = EmailPreferences.noPreferences): domain.models.developers.DeveloperSession = {

    val sessionId: String = UUID.randomUUID().toString

    domain.models.developers.DeveloperSession(
      loggedInState,
      sessionId,
      Developer(
        UserId.random,
        email,
        firstName,
        lastName,
        organisation,
        mfaEnabled,
        emailPreferences))
  }
}
