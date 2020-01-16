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

package utils

import org.jsoup.nodes.Document

import scala.collection.JavaConversions._

object ViewHelpers {

  def elementExistsByText(doc: Document, elementType: String, elementText: String): Boolean = {
    doc.select(elementType).exists(node => node.text.trim == elementText)
  }

  def elementExistsById(doc: Document, id: String): Boolean = doc.select(s"#$id").nonEmpty

  def elementExistsByAttr(doc: Document, elementType: String, attr: String): Boolean = {
    doc.select(s"$elementType[$attr]").nonEmpty
  }

  def elementExistsByAttrWithValue(doc: Document, elementType: String, attr: String, value: String): Boolean = {
    doc.select(s"$elementType[$attr=$value]").nonEmpty
  }

  def linkExistsWithHref(doc: Document, href: String): Boolean = {
    doc.select(s"a[href=$href]").nonEmpty
  }
//
//  def linkExistsWithHref(doc: Document, href: String): Boolean = {
//    doc.select(s"a[href=$href]").nonEmpty
//  }

  def formExistsWithAction(doc: Document, action: String): Boolean = {
    doc.select(s"form[action=$action]").nonEmpty
  }

  def inputExistsWithValue(doc: Document, id: String,  inputType: String, value: String): Boolean = {
    doc.select(s"input[id=$id][type=$inputType][value=$value]").nonEmpty
  }

  def textareaExistsWithText(doc: Document, id: String, text: String): Boolean = {
    elementIdentifiedByAttrWithValueContainsText(doc, "textarea", "id", id, text)
  }

  def elementIdentifiedByAttrContainsText(doc: Document, elementType: String, attr: String, text: String): Boolean = {
    doc.select(s"$elementType[$attr]").exists(element => element.text.equals(text))
  }

  def elementIdentifiedByAttrWithValueContainsText(doc: Document, elementType: String, attr: String, value: String, text: String): Boolean = {
    doc.select(s"$elementType[$attr=$value]").exists(element => element.text.equals(text))
  }

  def elementIdentifiedByIdContainsText(doc: Document, id: String, text: String): Boolean = {
    doc.select(s"#$id").exists(element => element.text.equals(text))
  }

  def elementIdentifiedByIdContainsValue(doc: Document, id: String, value: String): Boolean = {
    doc.select(s"#$id").exists(element => element.`val`.equals(value))
  }
}
