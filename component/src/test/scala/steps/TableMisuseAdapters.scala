/*
 * Copyright 2025 HM Revenue & Customs
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

package steps

import scala.jdk.CollectionConverters._
import io.cucumber.datatable.DataTable
import io.cucumber.scala.Implicits._

object TableMisuseAdapters {

  def asListOfKV(dataTable: DataTable): Map[String, String] = {
    dataTable.asScalaRawLists[String].map(_.toList match {
      case a :: b :: c => a -> b
      case _           => throw new RuntimeException("Badly constructed table")
    }).toMap
  }

  def valuesInColumn(n: Int)(data: DataTable): List[String] = {
    data.asLists().asScala.map(_.get(n)).toList
  }
}