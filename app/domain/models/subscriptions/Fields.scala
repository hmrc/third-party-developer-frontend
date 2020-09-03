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

package domain.models.subscriptions

case class FieldName(value: String) extends AnyVal

object FieldName {
  implicit val ordering: Ordering[FieldName] = new Ordering[FieldName] {
    override def compare(x: FieldName, y: FieldName): Int = x.value.compareTo(y.value)
  }
}

case class FieldValue(value: String) extends AnyVal {
  def isEmpty = value.isEmpty
}

object FieldValue {
  def empty = FieldValue("")

  import play.api.libs.json.Json
  val formatFieldValue = Json.valueFormat[FieldValue]
}

trait Fields {
  val empty = Map.empty[FieldName, FieldValue]
}

object Fields extends Fields {
  type Alias = Map[FieldName,FieldValue]
}
