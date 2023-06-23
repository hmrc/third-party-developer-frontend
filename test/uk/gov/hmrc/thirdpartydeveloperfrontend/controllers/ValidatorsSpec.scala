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

import scala.jdk.CollectionConverters._

import org.scalacheck.Gen
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

import play.api.data.Forms._
import play.api.data._
import play.api.data.validation.{Invalid, ValidationError, ValidationResult}

import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.AsyncHmrcSpec
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.Generators._

class ValidatorsSpec extends AsyncHmrcSpec with ScalaCheckPropertyChecks with Matchers {

  "firstnameValidator for the field firstname" should {
    val testForm = Form("firstname" -> firstnameValidator)

    "generate a field form error 'firstname.error.required.field' when empty string is provided" in {
      val res = testForm.bind(Map("firstname" -> ""))
      res.errors shouldBe List(FormError("firstname", "firstname.error.required.field"))
    }

    "generate a field form error 'firstname.error.maxLength.field' when string longer than 30 characters" in {
      val res = testForm.bind(Map("firstname" -> "1234567890123456789012345678901"))
      res.errors shouldBe List(FormError("firstname", "firstname.error.maxLength.field"))
    }

    "generate a field form error 'firstname.error.required.field' when string of 6 spaces is provided" in {
      val res = testForm.bind(Map("firstname" -> "      "))
      res.errors shouldBe List(FormError("firstname", "firstname.error.required.field"))
    }

    "generate 2 field form errors 'firstname.error.maxLength.field' and 'firstname.error.required.field' and  when string with 31 spaces is provided" in {
      val res = testForm.bind(Map("firstname" -> "                               "))
      res.errors shouldBe List(FormError("firstname", "firstname.error.required.field"))
    }

    "not generate a field form error when a correct value is privded" in {
      val res = testForm.bind(Map("firstname" -> "testName"))
      res.errors shouldBe List()
    }
  }

  "emailValidator for the field emailaddress" should {

    val myForm = Form("emailaddress" -> emailValidator())

    "generate a field form error 'emailaddress.error.required.field' when empty string is provided" in {
      val res = myForm.bind(Map("emailaddress" -> ""))
      res.errors shouldBe List(FormError("emailaddress", "emailaddress.error.required.field"))
    }

    "generate a field form error 'emailaddress.error.not.valid.field' when an invalid email is provided" in {
      val res = myForm.bind(Map("emailaddress" -> "test@"))
      res.errors shouldBe List(FormError("emailaddress", "emailaddress.error.not.valid.field"))
    }

    "generate a field form error 'emailaddress.error.not.valid.field' when an invalid email with 6 spaces is provided" in {
      val res = myForm.bind(Map("emailaddress" -> "      "))
      res.errors shouldBe List(FormError("emailaddress", "emailaddress.error.not.valid.field"))
    }

    "do not generate a field form error when a valid email 'admin@mailserver1.com' is provided" in {
      val res = myForm.bind(Map("emailaddress" -> "admin@mailserver1.com"))
      res.errors shouldBe List()
    }

    "no generate a field form error when a valid email 'test@example.com' is provided" in {
      val res = myForm.bind(Map("emailaddress" -> "test@example.com"))
      res.errors shouldBe List()
    }
  }

  "password validation" should {
    val requiredCharacters: Gen[Seq[Char]] =
      Gen
        .sequence(Seq(asciiLower, asciiUpper, asciiDigit, asciiSpecial))
        .map(_.asScala.toSeq)

    def passwordPaddedToLength(n: Int) =
      for {
        required <- requiredCharacters
        padding  <- Gen.listOfN(Math.max(0, n - required.length), asciiPrintable)
        padded    = required ++ padding
        shuffled <- shuffle(padded)
      } yield shuffled.mkString

    val password = Gen.choose(12, 1000).flatMap(passwordPaddedToLength)

    val shortPassword = Gen.choose(0, 11).flatMap(passwordPaddedToLength)

    val blank = Gen.listOf(" ").map(_.mkString)

    val passwordNotValidError = FormError("password", "password.error.not.valid.field")
    val passwordRequiredError = FormError("password", "password.error.required.field")
    val testForm              = Form("password" -> passwordValidator)

    def passwordErrors(password: String) = testForm.bind(Map("password" -> password)).errors

    "return not valid and required errors if a blank password is provided (either nothing, or all spaces)" in forAll(blank) {
      passwordErrors(_) shouldBe List(passwordNotValidError, passwordRequiredError)
    }

    "return not valid error if password is missing upper, lower, digits or specials" in {
      passwordErrors("HasNoDigitsIncluded!") shouldBe List(passwordNotValidError)
      passwordErrors("HasNoSpecialsIncluded1") shouldBe List(passwordNotValidError)
      passwordErrors("hasnouppercaseincluded1!") shouldBe List(passwordNotValidError)
      passwordErrors("HASNOLOWERCASEINCLUDED1!") shouldBe List(passwordNotValidError)
    }

    "return not valid error if password is too short" in forAll(shortPassword) {
      passwordErrors(_) shouldBe List(passwordNotValidError)
    }

    "return no errors if valid password provided" in forAll(password) {
      passwordErrors(_) shouldBe List()
    }
  }

  "password match validation" should {

    "return matching error if valid password but confirm field does not match" in {
      val res: ValidationResult = passwordsMatch.apply(RegisterForm("name", "last name", "john@example.com", "A1@wwwwwwwww", "somethingelse"))

      res match {
        case v: Invalid => v.errors shouldBe List(ValidationError("password.error.no.match.global"))
        case _          => fail("passwords matching validation should have failed")
      }
    }

    "return matching error if valid password but confirm field is empty" in {
      val res: ValidationResult = passwordsMatch.apply(RegisterForm("name", "last name", "john@example.com", "A1@wwwwwwwww", ""))

      res match {
        case v: Invalid => v.errors shouldBe List(ValidationError("password.error.no.match.global"))
        case _          => fail("passwords matching validation should have failed")
      }
    }

    "return matching error if valid password but confirm field is only spaces" in {
      val res: ValidationResult = passwordsMatch.apply(RegisterForm("name", "last name", "john@example.com", "A1@wwwwwwwww", "     "))

      res match {
        case v: Invalid => v.errors shouldBe List(ValidationError("password.error.no.match.global"))
        case _          => fail("passwords matching validation should have failed")
      }
    }

    "return no errors if valid password and fields match" in {
      val res: ValidationResult = passwordsMatch.apply(RegisterForm("name", "last name", "john@example.com", "A1@wwwwwwwww", "A1@wwwwwwwww"))

      res match {
        case v: Invalid => fail("passwords matching validation should have succeeded")
        case _          =>
      }
    }
  }

  "redirectUri validation" should {
    val form = Form(single("redirectUri" -> redirectUriValidator))

    val invalidCases = Map(
      "fragment in http url"      -> "http://example.com#test",
      "fragment in https url"     -> "https://example.com#test",
      "fragment in localhost url" -> "http://localhost#test",
      "invalid url"               -> "random",
      "not https"                 -> "http://example.com",
      "invalid localhost"         -> "http://localhost.example.com"
    )

    val validCases = Map(
      "localhost"           -> "http://localhost",
      "localhost with port" -> "http://localhost:8080",
      "localhost with path" -> "http://localhost:8080/some/path",
      "https url"           -> "https://example.com",
      "oob"                 -> "urn:ietf:wg:oauth:2.0:oob",
      "oob auto"            -> "urn:ietf:wg:oauth:2.0:oob:auto"
    )

    for ((k, v) <- invalidCases) {
      s"reject redirect uri for $k url" in {
        val result = form.fillAndValidate(v)
        result.errors.map(_.message) shouldBe Seq(FormKeys.redirectUriInvalidKey)
      }
    }

    for ((k, v) <- validCases) {
      s"accept redirect uri for $k" in {
        val result = form.fillAndValidate(v)
        result.errors shouldBe Nil
      }
    }
  }
}
