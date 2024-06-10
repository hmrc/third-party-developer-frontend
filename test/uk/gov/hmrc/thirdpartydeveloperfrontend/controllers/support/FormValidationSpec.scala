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
import play.api.data.{Form, FormError, Mapping}

import uk.gov.hmrc.apiplatform.modules.common.utils.HmrcSpec

class FormValidationSpec extends HmrcSpec {

  import FormValidation._

  case class TestForm(value: String)

  val testValidator: (String, String) => Tuple2[String, Mapping[String]] = (fieldName, messagePrefix) =>
    (
      fieldName -> text.verifying(s"$messagePrefix.error", s => !s.isBlank())
    )

  "simple case" should {
    val v = "aaa" ~> testValidator
    v._1 shouldBe "aaa"

    "pass validation" in {
      val bound = Form(v).bind(Map("aaa" -> "123"))
      bound.errors shouldBe Seq.empty
    }

    "return the correct error message" in {
      val bound2 = Form(v).bind(Map("aaa" -> ""))
      bound2.errors shouldBe Seq(FormError("aaa", "aaa.error"))
    }
  }

  "extended case should work" should {
    val v = "xxx" ~> "aaa" ~> testValidator
    v._1 shouldBe "aaa"

    "pass validation" in {
      val bound = Form(v).bind(Map("aaa" -> "123"))
      bound.errors shouldBe Seq.empty
    }

    "return the correct error message" in {
      val bound2 = Form(v).bind(Map("aaa" -> ""))
      bound2.errors shouldBe Seq(FormError("aaa", "xxx.aaa.error"))
    }
  }

  "super extended case should work" should {
    val v = "yyy" ~> "xxx" ~> "aaa" ~> testValidator
    v._1 shouldBe "aaa"

    "pass validation" in {
      val bound = Form(v).bind(Map("aaa" -> "123"))
      bound.errors shouldBe Seq.empty
    }

    "return the correct error message" in {
      val bound2 = Form(v).bind(Map("aaa" -> ""))
      bound2.errors shouldBe Seq(FormError("aaa", "yyy.xxx.aaa.error"))
    }
  }
}
