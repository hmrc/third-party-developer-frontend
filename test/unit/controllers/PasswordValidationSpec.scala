/*
 * Copyright 2018 HM Revenue & Customs
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

package unit.controllers

import controllers.passwordValidator
import org.scalatest.Matchers
import play.api.data.{Form, FormError}
import uk.gov.hmrc.play.test.UnitSpec


class PasswordValidationSpec extends UnitSpec with Matchers{
  "passwordValidator for the field password" should {
    val testForm = Form("password" -> passwordValidator)

    "generate an error when empty" in {
      val res = testForm.bind(Map("password" -> ""))
      res.errors shouldBe List(
        FormError("password", "password.error.not.valid.field"),
        FormError("password", "password.error.required.field")
      )
    }

    "generate an error when not valid" in {
      val res = testForm.bind(Map("password" -> "notstrongenough"))
      res.errors shouldBe List(
        FormError("password", "password.error.not.valid.field")
      )
    }

    "generate an error when not valid due to no special chars" in {
      val res = testForm.bind(Map("password" -> "qxeFq9IJ7xBIs"))
      res.errors shouldBe List(
        FormError("password", "password.error.not.valid.field")
      )
    }

    "generate no error when valid" in {
      val res = testForm.bind(Map("password" -> "A1@wwwwwwwww"))
      res.errors shouldBe List()
    }

    val specialCharacters = """ !"#$%&'()*+,-./:;<=>?@[\]^_`{|}~"""
    specialCharacters.toCharArray.foreach(specialChar=>
      s"accept special character [$specialChar] in password" in {
            testForm.bind(Map("password" -> s"A1${specialChar}wwwwwwwww")).errors shouldBe List()
      }
    )
  }
}
