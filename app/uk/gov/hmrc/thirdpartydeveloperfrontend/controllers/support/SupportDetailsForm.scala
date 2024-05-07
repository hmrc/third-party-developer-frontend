/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.support

import play.api.data.Form
import play.api.data.Forms._

import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.{emailValidator, fullnameValidator, supportRequestValidator}

final case class SupportDetailsForm(details: String, fullName: String, emailAddress: String, organisation: Option[String])

object SupportDetailsForm {

  val form: Form[SupportDetailsForm] = Form(
    mapping(
      "details"      -> supportRequestValidator("support.details.required.field", "support.details.required.field", 3000),
      "fullName"     -> fullnameValidator,
      "emailAddress" -> emailValidator(),
      "organisation" -> optional(text)
    )(SupportDetailsForm.apply)(SupportDetailsForm.unapply)
  )
}
