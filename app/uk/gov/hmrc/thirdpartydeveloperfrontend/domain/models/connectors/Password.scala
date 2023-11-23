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

package uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors

import play.api.libs.json._

import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress

case class PasswordReset(email: LaxEmailAddress, newPassword: String)

object PasswordReset {
  implicit val format: OFormat[PasswordReset] = Json.format[PasswordReset]
}

case class ChangePassword(email: LaxEmailAddress, oldPassword: String, newPassword: String)

object ChangePassword {
  implicit val format: OFormat[ChangePassword] = Json.format[ChangePassword]
}
