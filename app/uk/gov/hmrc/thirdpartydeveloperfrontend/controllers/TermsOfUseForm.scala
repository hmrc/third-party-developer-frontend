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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers

import play.api.data.Form
import play.api.data.Forms.{boolean, mapping}

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.CheckInformation
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.Conversions._
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.FormKeys.termsOfUseAgreeKey

case class TermsOfUseForm(termsOfUseAgreed: Boolean)

object TermsOfUseForm {

  def form: Form[TermsOfUseForm] = Form(
    mapping(
      "termsOfUseAgreed" -> boolean.verifying(termsOfUseAgreeKey, b => b)
    )(TermsOfUseForm.apply)(TermsOfUseForm.unapply)
  )

  def fromCheckInformation(checkInformation: CheckInformation) = {
    TermsOfUseForm(checkInformation.termsOfUseAgreements.nonEmpty)
  }
}
