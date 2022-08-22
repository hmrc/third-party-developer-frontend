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

import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.{Developer, UserId}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.emailpreferences.EmailPreferences

trait HasUserData {
  lazy val userEmail = "user@example.com"
  lazy val userId = UserId.random
  lazy val userFirstName = "Bob"
  lazy val userLastName = "Example"
  lazy val userPassword = "S3curE-Pa$$w0rd!"
  lazy val user = Developer(
    userId, userEmail, userFirstName, userLastName, None, List.empty, EmailPreferences.noPreferences
  )

}
