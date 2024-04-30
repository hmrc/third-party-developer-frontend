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

import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.{emailValidator, fullnameValidator}

final case class ApplyForPrivateApiAccessForm(fullName: String, emailAddress: String, organisation: String, applicationId: String)

object ApplyForPrivateApiAccessForm {

  val form: Form[ApplyForPrivateApiAccessForm] = Form(
    mapping(
      "fullName"      -> fullnameValidator,
      "emailAddress"  -> emailValidator(),
      "organisation"  -> nonEmptyText,
      "applicationId" -> nonEmptyText
    )(ApplyForPrivateApiAccessForm.apply)(ApplyForPrivateApiAccessForm.unapply)
  )
}
