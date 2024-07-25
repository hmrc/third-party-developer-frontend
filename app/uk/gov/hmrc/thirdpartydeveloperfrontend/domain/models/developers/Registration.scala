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

package uk.gov.hmrc.apiplatform.modules.tpd.domain.models

import play.api.libs.json._

import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress

case class Registration(firstName: String, lastName: String, email: LaxEmailAddress, password: String, organisation: Option[String] = None)

object Registration {
  implicit val registrationFmt: OFormat[Registration] = Json.format[Registration]
}

sealed trait RegistrationDownstreamResponse

case object RegistrationSuccessful extends RegistrationDownstreamResponse

case object EmailAlreadyInUse extends RegistrationDownstreamResponse
