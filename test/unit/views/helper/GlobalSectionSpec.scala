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

package unit.views.helper

import org.scalatest.Matchers
import uk.gov.hmrc.play.test.UnitSpec
import views.helper.GlobalSection

class GlobalSectionSpec extends UnitSpec with Matchers {

  "dataAttribute" should {
    Seq(
      "firstname.error.required.global" -> "data-global-error-firstname",
      "firstname.error.maxLength.global" -> "data-global-error-firstname",
      "lastname.error.required.global" -> "data-global-error-lastname",
      "lastname.error.maxLength.global" -> "data-global-error-lastname",
      "emailaddress.error.required.global" -> "data-global-error-emailaddress",
      "emailaddress.error.not.valid.global" -> "data-global-error-emailaddress",
      "password.error.required.global" -> "data-global-error-password",
      "password.error.not.valid.global" -> "data-global-error-password",
      "password.error.no.match.global" -> "data-global-error-password",
      "emailaddress.already.registered.global" -> "data-global-error-emailaddress"

    ).foreach {
      case (k, v) =>
        s"generate the data attribute '$v' when the error is '$k'" in {
          GlobalSection.dataAttribute(k) shouldBe v
        }
    }

    "generate the data attribute 'data-global-error-undefined' when the error is 'some.error.required.global'" in {
      GlobalSection.dataAttribute("some.error.required.global") shouldBe "data-global-error-undefined"
    }

    "generate the data attribute 'data-global-error-undefined' when the error is 'something not expected'" in {
      GlobalSection.dataAttribute("something not expected") shouldBe "data-global-error-undefined"
    }
  }



  "anchor" should {
    Seq(
      "firstname.error.required.global" -> "#firstname",
      "firstname.error.maxLength.global" -> "#firstname",
      "lastname.error.required.global" -> "#lastname",
      "lastname.error.maxLength.global" -> "#lastname",
      "emailaddress.error.required.global" -> "#emailaddress",
      "emailaddress.error.not.valid.global" -> "#emailaddress",
      "password.error.required.global" -> "#password",
      "password.error.not.valid.global" -> "#password",
      "password.error.no.match.global" -> "#password",
      "emailaddress.already.registered.global" -> "#emailaddress"
    ).foreach {
      case (k, v) =>
        s"generate the anchor'$v' when the error is '$k'" in {
          GlobalSection.anchor(k) shouldBe v
        }
    }

    "generate the data attribute '#section-undefined' when the error is 'some.error.required.global'" in {
      GlobalSection.anchor("some.error.required.global") shouldBe "#section-undefined"
    }

    "generate the data attribute '#section-undefined' when the error is 'something not expected'" in {
      GlobalSection.anchor("something not expected") shouldBe "#section-undefined"
    }
  }


}
