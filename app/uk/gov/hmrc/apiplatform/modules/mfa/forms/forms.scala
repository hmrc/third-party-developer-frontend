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

package uk.gov.hmrc.apiplatform.modules.mfa.forms

import play.api.data.Form
import play.api.data.Forms.{boolean, mapping, nonEmptyText, text}
import uk.gov.hmrc.apiplatform.modules.mfa.models.MfaType
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.FormKeys

import java.util.regex.Pattern

final case class MfaAccessCodeForm(accessCode: String, rememberMe: Boolean)

object MfaAccessCodeForm {
  def form: Form[MfaAccessCodeForm] = Form(
    mapping(
      "accessCode" -> text.verifying(FormKeys.accessCodeInvalidKey, s => s.matches("^[0-9]{6}$")),
      "rememberMe" -> boolean

    )(MfaAccessCodeForm.apply)(MfaAccessCodeForm.unapply)
  )
}

final case class MfaNameChangeForm(name: String )

object MfaNameChangeForm {
  def form: Form[MfaNameChangeForm] = Form(
    mapping("name" -> text.verifying(FormKeys.mfaNameChangeInvaliddKey, s => s.length > 3))
    (MfaNameChangeForm.apply)(MfaNameChangeForm.unapply)
  )
}

final case class MobileNumberForm(mobileNumber: String )

object MobileNumberForm {
  def form: Form[MobileNumberForm] = Form(
    mapping("mobileNumber" -> text.verifying(FormKeys.mobileNumberInvaliddKey, s => isValidPhoneNumber(s)))
    (MobileNumberForm.apply)(MobileNumberForm.unapply)
  )

  def isValidPhoneNumber(phoneNumber: String): Boolean = {
    Pattern.compile("[+()-0123456789\\s]+").matcher(phoneNumber).matches()
  }
}

final case class SmsAccessCodeForm(accessCode: String, mobileNumber: String)

object SmsAccessCodeForm {
  def form: Form[SmsAccessCodeForm] = Form(
    mapping(
      "accessCode" -> text.verifying(FormKeys.accessCodeInvalidKey, s => s.matches("^[0-9]{6}$")),
      "mobileNumber" -> text
    )(SmsAccessCodeForm.apply)(SmsAccessCodeForm.unapply)
  )
}

final case class SelectMfaForm(mfaType: String)

object SelectMfaForm {

  def form: Form[SelectMfaForm] = Form(
    mapping(
    "mfaType" -> text.verifying(FormKeys.selectMfaInvalidKey, s => verifyMfaType(s))
    )
  (SelectMfaForm.apply)(SelectMfaForm.unapply)
  )

  def verifyMfaType(mfaType: String) = {
    MfaType.values.exists(v => v.entryName.equalsIgnoreCase(mfaType))
  }
}

final case class SelectLoginMfaForm(mfaId: String)

object SelectLoginMfaForm {

  def form: Form[SelectLoginMfaForm] = Form(
    mapping(
      "mfaId" -> nonEmptyText
    )
    (SelectLoginMfaForm.apply)(SelectLoginMfaForm.unapply)
  )
}




