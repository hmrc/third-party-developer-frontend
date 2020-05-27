/*
 * Copyright 2020 HM Revenue & Customs
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

package controllers

import org.scalatest.Matchers
import play.api.data.{Form, FormError}
import uk.gov.hmrc.play.test.UnitSpec

class EmailValidationSpec extends UnitSpec with Matchers{
  "emailValidator for the field email" should {
    val testForm = Form("emailaddress" -> emailValidator())

    "generate no error when valid " in {
      val res = testForm.bind(Map("emailaddress" -> "john.smith+info@example.com"))
      res.errors shouldBe List()
    }

    "generate an error when empty" in {
      val res = testForm.bind(Map("emailaddress" -> ""))
      res.errors shouldBe List(
        FormError("emailaddress", "emailaddress.error.required.field")
      )
    }

    "generate an error when max length exceeded" in {
      val email = ("abc" * 105) + "@example.com"
      val res = testForm.bind(Map("emailaddress" -> email))
      res.errors shouldBe List(
        FormError("emailaddress", "emailaddress.error.maxLength.field")
      )
    }

    val ValidEmailAddresses = Seq("prettyandsimple@example.com", "very.common@example.com",
      "disposable.style.email.with+symbol@example.com", "other.email-with-dash@example.com")

    ValidEmailAddresses foreach { emailAddress =>
      s"accept a valid email address [$emailAddress]" in {
        val res = testForm.bind(Map("emailaddress" -> emailAddress))
        res.errors shouldBe Nil
      }
    }

    val notValidEmailAddresses = Map("Abc.example.com" -> "no @ character",
      "A@b@c@example.com" -> "only one @ is allowed outside quotation marks",
      """a\"b(c)d,e:f;g<h>i[j\\k]l@example.com""" -> "none of the special characters in this local part are allowed outside quotation marks",
      "john.doe@example..com" -> "double dot after @",
      "john.doe@example." -> "dot at the end")

    notValidEmailAddresses foreach { case (emailAddress, notValidReason) =>
      s"generate an error for not valid email address [$emailAddress] due to [$notValidReason]" in {
        val res = testForm.bind(Map("emailaddress" -> emailAddress))
        res.errors shouldBe List(FormError("emailaddress", "emailaddress.error.not.valid.field"))
      }
    }
  }

}
