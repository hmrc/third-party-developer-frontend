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

import play.api.data.Forms._
import play.api.data.{Form, Mapping}

final case class SupportEnquiryForm(fullname: String, email: String, comments: String)

object SupportEnquiryForm extends FormValidation {

  private def commentsValidator(fieldName: String, messagePrefix: String): Tuple2[String, Mapping[String]] = {
    val spambotCommentRegex = """(?i).*Como.+puedo.+iniciar.*""".r
    (
      fieldName -> default(text, "")
        .verifying(s"$messagePrefix.error.required.field", s => !s.isBlank())
        .verifying(s"$messagePrefix.error.maxLength.field", s => s.trim.length <= 3000)
        .verifying(s"$messagePrefix.error.spam.field", s => spambotCommentRegex.findFirstMatchIn(s).isEmpty)
    )
  }

  val form: Form[SupportEnquiryForm] = Form(
    mapping(
      "fullname" ~> requiredLimitedTextValidator(100),
      "emailaddress" ~> emailValidator,
      "comments" ~> commentsValidator
    )(SupportEnquiryForm.apply)(SupportEnquiryForm.unapply)
  )
}
