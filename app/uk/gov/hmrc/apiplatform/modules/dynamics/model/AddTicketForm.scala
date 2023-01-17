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

package uk.gov.hmrc.apiplatform.modules.dynamics.model

import play.api.data.Form
import play.api.data.Forms._

final case class AddTicketForm(customerId: String, title: String, description: String)

object AddTicketForm {

  def form: Form[AddTicketForm] = Form(
    mapping(
      "customerId"  -> text.verifying(
        "This field is a UUID (e.g. 7e88b5e8-8924-ed11-9db2-0022481a611c)",
        s => s.toLowerCase().matches("^[{]?[0-9a-f]{8}-([0-9a-f]{4}-){3}[0-9a-f]{12}[}]?$")
      ),
      "title"       -> nonEmptyText,
      "description" -> nonEmptyText
    )(AddTicketForm.apply)(AddTicketForm.unapply)
  )
}
