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

import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.ErrorFormBuilder.GlobalError
import play.api.data.{Form, FormError, Forms}
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.AsyncHmrcSpec

class GlobalErrorSpec extends AsyncHmrcSpec {

  "firstnameGlobal" should {

    "add a global form error 'firstname.error.required.global' when a field form error 'firstname.error.required.field'" in {
      val testForm = Form("firstname" -> firstnameValidator).bind(Map("firstname" -> ""))
      val boundWithGlobalErrors = testForm.firstnameGlobal()
      boundWithGlobalErrors.globalErrors shouldBe Seq(FormError("", "firstname.error.required.global"))
    }

    "add a global form error 'firstname.error.maxLength.global' when a field form error 'firstname.error.maxLength.field'" in {
      val testForm = Form("firstname" -> firstnameValidator).bind(Map("firstname" -> "01234567890123456789012345678901"))
      val boundWithGlobalErrors = testForm.firstnameGlobal()
      boundWithGlobalErrors.globalErrors shouldBe Seq(FormError("", "firstname.error.maxLength.global"))
    }

    "add a global form error 'firstname.error.required.global' when 2 field form errors 'firstname.error.maxLength.field' and 'firstname.error.required.field'" in {
      val testForm = Form("firstname" -> firstnameValidator).bind(Map("firstname" -> "                               "))
      val boundWithGlobalErrors = testForm.firstnameGlobal()
      boundWithGlobalErrors.globalErrors shouldBe Seq(FormError("", "firstname.error.required.global"))
    }

    "not add global form error when a valid value is provided" in {
      val testForm = Form("firstname" -> firstnameValidator).bind(Map("firstname" -> "testName"))
      val boundWithGlobalErrors = testForm.firstnameGlobal()
      boundWithGlobalErrors.globalErrors shouldBe Seq()
    }
  }

  "emailaddressGlobal" should {

    "add a global form error 'emailaddress.error.required.global' when 2 field form error 'emailaddress.error.not.valid.field' and 'emailaddress.error.required.field'" in {
      val testForm = Form("emailaddress" -> emailValidator()).bind(Map("emailaddress" -> ""))
      val boundWithGlobalErrors = testForm.emailaddressGlobal()
      boundWithGlobalErrors.globalErrors shouldBe Seq(FormError("", "emailaddress.error.required.global"))
    }

    "add a global form error 'emailaddress.error.not.valid.global' when a field form error 'emailaddress.error.not.valid.field'" in {
      val testForm = Form("emailaddress" -> emailValidator()).bind(Map("emailaddress" -> "test@"))
      val boundWithGlobalErrors = testForm.emailaddressGlobal()
      boundWithGlobalErrors.globalErrors shouldBe Seq(FormError("", "emailaddress.error.not.valid.global"))
    }

    "add a global form error 'emailaddress.error.not.valid.global' when a field form error 'emailaddress.error.not.valid.field' for string of spaces" in {
      val testForm = Form("emailaddress" -> emailValidator()).bind(Map("emailaddress" -> "     "))
      val boundWithGlobalErrors = testForm.emailaddressGlobal()
      boundWithGlobalErrors.globalErrors shouldBe Seq(FormError("", "emailaddress.error.not.valid.global"))
    }

    "not add global form error when a valid value is provided" in {
      val testForm = Form("emailaddress" -> emailValidator()).bind(Map("emailaddress" -> "someTest@example.com"))
      val boundWithGlobalErrors = testForm.emailaddressGlobal()
      boundWithGlobalErrors.globalErrors shouldBe Seq()
    }
  }

  "firstnameGlobal and emailaddressGlobal" should {
    "add to 2 global form errors 'firstname.error.required.global' and 'emailaddress.error.required.global' " in {
      val testForm = Form(Forms.tuple("firstname" -> firstnameValidator, "emailaddress" -> emailValidator())).bind(Map("firstname" -> "testName"))

      val boundWithGlobalErrors = testForm.bind(Map("firstname" -> "", "emailaddress" -> "")).firstnameGlobal().emailaddressGlobal()

      boundWithGlobalErrors.globalErrors shouldBe Seq(
        FormError("", "firstname.error.required.global"),
        FormError("", "emailaddress.error.required.global")
      )
    }
  }

  "passwordNoMatchField" should {
    "generate a password.error.no.match.field validation error when a global error is present" in {

      val boundWithErrors = RegistrationForm.form.bind(
        Map(
          "firstname" -> "john",
          "lastname" -> "smith",
          "emailaddress" -> "john@example.com",
          "password" -> "A1@wwwwwwwww",
          "confirmpassword" -> "www"
        )
      )

      boundWithErrors.errors("password") shouldBe Nil
      boundWithErrors.globalErrors shouldBe List(FormError("", "password.error.no.match.global"))
      boundWithErrors.passwordNoMatchField().errors("password") shouldBe List(FormError("password", "password.error.no.match.field"))
    }
  }

  "isEmailAddressAlreadyUsed" should {
    "return true when the email address is already used" in {
      val testForm = Form("emailaddress" -> emailValidator())
        .bind(Map("emailaddress" -> "test@example.com"))
        .emailAddressAlreadyInUse

      testForm.isEmailAddressAlreadyUse shouldBe true
    }

    "return false when the email address is already used" in {
      val testForm = Form("emailaddress" -> emailValidator()).bind(Map("emailaddress" -> "test@example.com"))
      testForm.errors shouldBe Nil

      testForm.isEmailAddressAlreadyUse shouldBe false
    }
  }
}
