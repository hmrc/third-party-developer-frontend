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

import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.AsyncHmrcSpec
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.BuildValidateNoErrors

class SupportEnquiryFormSpec extends AsyncHmrcSpec with BuildValidateNoErrors {
  
  "SupportEnquiryForm" should {
    def validateNoErrors = buildValidateNoErrors(SupportEnquiryForm.form.bind) _

    val validFormData = Map("fullname" -> "Terry Jones", "emailaddress" -> "test@example.com", "comments" -> "this is fine")

    "accept valid form" in {
      validateNoErrors(validFormData)
    }

    "accept comments with up to 3000 chars." in {
      val formData = validFormData + ("comments" -> "a" * 3000)
      validateNoErrors(formData)
    }

    "reject when comments with more than 3000 charaters" in {
      val formData  = validFormData + ("comments" -> "a" * 3001)
      val boundForm = SupportEnquiryForm.form.bind(formData)
      val err       = boundForm.errors.head
      err.key shouldBe "comments"
      err.messages shouldBe List("comments.error.maxLength.field")
    }

    "reject a form that has missing comments" in {
      val formData  = validFormData - "comments"
      val boundForm = SupportEnquiryForm.form.bind(formData)
      val err       = boundForm.errors.head
      err.key shouldBe "comments"
      err.messages shouldBe List("error.required")
    }

    "reject a form that has missing name" in {
      val formData  = validFormData - "fullname"
      val boundForm = SupportEnquiryForm.form.bind(formData)
      val err       = boundForm.errors.head
      err.key shouldBe "fullname"
      err.messages shouldBe List("error.required")
    }

    "reject a form that when the name is too long" in {
      val formData  = validFormData + ("fullname" -> "a" * 101)
      val boundForm = SupportEnquiryForm.form.bind(formData)
      val err       = boundForm.errors.head
      err.key shouldBe "fullname"
      err.messages shouldBe List("fullname.error.maxLength.field")
    }

    "reject a form that has missing email address" in {
      val formData  = validFormData - "emailaddress"
      val boundForm = SupportEnquiryForm.form.bind(formData)
      val err       = boundForm.errors.head
      err.key shouldBe "emailaddress"
      err.messages shouldBe List("error.required")
    }

    "reject a form that when the email is too long" in {
      val formData  = validFormData + ("emailaddress" -> s"${"a" * 320}@example.com")
      val boundForm = SupportEnquiryForm.form.bind(formData)
      val err       = boundForm.errors.head
      err.key shouldBe "emailaddress"
      err.messages shouldBe List("emailaddress.error.maxLength.field")
    }
  } 
}
