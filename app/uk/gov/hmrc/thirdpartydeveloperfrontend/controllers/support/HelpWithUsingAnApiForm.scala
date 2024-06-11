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

final case class HelpWithUsingAnApiForm(choice: String, apiNameForCall: String, apiNameForExamples: String, apiNameForReporting: String)

object HelpWithUsingAnApiForm extends FormValidation {
  private val formPrefix = "help.with.using.api"

  val form: Form[HelpWithUsingAnApiForm] = Form(
    mapping(
      formPrefix ~> "choice" ~> requiredLimitedTextValidator(150), // TODO - one of the options or else
      // TODO - review the naming below
      SupportData.MakingAnApiCall.id + "-api-name"        -> nonEmptyText,
      SupportData.GettingExamples.id + "-api-name"        -> nonEmptyText,
      SupportData.ReportingDocumentation.id + "-api-name" -> nonEmptyText
    )(HelpWithUsingAnApiForm.apply)(HelpWithUsingAnApiForm.unapply)
  )
}
