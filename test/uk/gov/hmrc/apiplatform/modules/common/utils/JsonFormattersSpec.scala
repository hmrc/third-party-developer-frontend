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

package uk.gov.hmrc.apiplatform.modules.common.utils

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import play.api.libs.json._

trait JsonFormattersSpec extends AnyWordSpec with Matchers {

  def testToJson[T](in: T)(fields: (String, String)*)(implicit wrt: Writes[T]) = {
    val f: Seq[(String, JsValue)] = fields.map { case (k, v) => (k -> JsString(v)) }
    Json.toJson(in) shouldBe JsObject(f)
  }

  def testFromJson[T](text: String)(expected: T)(implicit rdr: Reads[T]) =
    Json.parse(text).validate[T] match {
      case JsSuccess(found, _) if (found == expected) => succeed
      case JsSuccess(found, _)                        => fail(s"Did not get $expected (got $found instead)")
      case _                                          => fail(s"Did not succeed")
    }
}
