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

package uk.gov.hmrc.apiplatform.modules.submissions.domain.utils

import play.api.libs.json.*

class InvalidEnumException(className: String, input: String)
    extends RuntimeException(s"Enumeration expected of type: '$className', but it does not contain '$input'")

object EnumJson {

  def enumReads[E <: Enumeration](enumValue: E): Reads[enumValue.Value] = new Reads[enumValue.Value] {

    def reads(json: JsValue): JsResult[enumValue.Value] = json match {
      case JsString(s) =>
        try {
          JsSuccess(enumValue.withName(s))
        } catch {
          case _: NoSuchElementException =>
            throw new InvalidEnumException(enumValue.getClass.getSimpleName, s)
        }
      case _           => JsError("String value expected")
    }
  }

  def enumWrites[E <: Enumeration](enumValue: E): Writes[enumValue.Value] = new Writes[enumValue.Value] {
    def writes(v: enumValue.Value): JsValue = JsString(v.toString)
  }

  import scala.language.implicitConversions

  implicit def enumFormat[E <: Enumeration](enumValue: E): Format[enumValue.Value] = {
    Format(enumReads(enumValue), enumWrites(enumValue))
  }

}
