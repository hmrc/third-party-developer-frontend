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

package domain.models.applications


case class OverrideFlag(overrideType: String)

object OverrideFlag {
  import play.api.libs.json._

  val reads = Reads[OverrideFlag] {
    case JsString(value) => JsSuccess(OverrideFlag(value))
    case o: JsObject => Json.reads[OverrideFlag].reads(o)
    case _ => JsError()
  }

  val writes = Json.writes[OverrideFlag]
  implicit val format = Format(reads, writes)
}
