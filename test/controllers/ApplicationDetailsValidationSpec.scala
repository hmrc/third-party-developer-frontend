/*
 * Copyright 2021 HM Revenue & Customs
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

import play.api.data.{Form, FormError}
import utils.AsyncHmrcSpec

class ApplicationDetailsValidationSpec extends AsyncHmrcSpec {

  "applicationNameValidator" should {
    val testForm = Form("name" -> applicationNameValidator)
    val applicationNameInvalidKeyLengthAndCharacters = "application.name.invalid.length.and.characters"

    "generate no error when valid" in {
      val res = testForm.bind(Map("name" -> "Parsley App"))
      res.errors shouldBe List()
    }

    "should not generate an error at 2 characters" in {
      val res = testForm.bind(Map("name" -> "Hi"))
      res.errors shouldBe List()
    }

    "should not generator an error at 50 characters" in {
      val name = "a" * 50
      val res = testForm.bind(Map("name" -> name))

      res.errors shouldBe List()
    }

    "generate an error when empty, or one character long" in {
      val expectedErrors = List(FormError("name", applicationNameInvalidKeyLengthAndCharacters))

      testForm.bind(Map("name" -> "")).errors shouldBe expectedErrors
      testForm.bind(Map("name" -> "a")).errors shouldBe expectedErrors
    }

    "generate an error when max length exceeded" in {
      val name = "a" * 51
      val expectedErrors = List(FormError("name", applicationNameInvalidKeyLengthAndCharacters))

      val res = testForm.bind(Map("name" -> name))
      res.errors shouldBe expectedErrors
    }

    "generate an error when non ASCII characters are used" in {
      val name = "ddɐ ʎǝlsɹɐԀ"
      val expectedErrors = List(FormError("name", applicationNameInvalidKeyLengthAndCharacters))
      val res = testForm.bind(Map("name" -> name))
      res.errors shouldBe expectedErrors
    }
  }
}
