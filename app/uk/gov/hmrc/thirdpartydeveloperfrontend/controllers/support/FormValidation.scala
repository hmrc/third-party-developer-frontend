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

import cats.data.NonEmptyList
import org.apache.commons.validator.routines.EmailValidator

import play.api.data.Forms._
import play.api.data.Mapping

trait FormValidation {

  implicit class FieldNameSyntax(firstFragment: String) {

    def ~>[T](fn: (String, String) => Tuple2[String, Mapping[T]]) = {
      fn(firstFragment, firstFragment.toLowerCase())
    }

    def ~>[T](nextFragment: String) = new FormAndFieldSyntax(NonEmptyList.of(nextFragment, firstFragment))
  }

  class FormAndFieldSyntax(fragments: NonEmptyList[String]) {

    def ~>[T](fn: (String, String) => Tuple2[String, Mapping[T]]) = {
      fn(fragments.head, fragments.toList.reverse.mkString(".").toLowerCase())
    }

    def ~>[T](nextFragment: String) = new FormAndFieldSyntax(nextFragment :: fragments)
  }

  def textValidator: (String, String) => Tuple2[String, Mapping[String]] = requiredLimitedTextValidator(30)

  def requiredLimitedTextValidator(maxLength: Int)(fieldName: String, messagePrefix: String): Tuple2[String, Mapping[String]] =
    (
      fieldName -> default(text, "")
        .verifying(s"$messagePrefix.error.required.field", s => !s.isBlank())
        .verifying(s"$messagePrefix.error.maxLength.field", s => s.trim.length <= maxLength)
    )

  private val emailValidator = EmailValidator.getInstance()

  def emailValidator(fieldName: String, messagePrefix: String): Tuple2[String, Mapping[String]] =
    fieldName -> default(text, "")
      .verifying(s"$messagePrefix.error.required.field", s => !s.isBlank)
      .verifying(s"$messagePrefix.error.maxLength.field", s => s.trim.length <= 320)
      .verifying(s"$messagePrefix.error.not.valid.field", s => s.isBlank || s.trim.length > 320 || emailValidator.isValid(s))

  def optionalEmailValidator(fieldName: String, messagePrefix: String): Tuple2[String, Mapping[Option[String]]] =
    (
      fieldName -> optional(text)
        .verifying(s"$messagePrefix.error.maxLength.field", s => s.fold(true)(_.trim.length <= 320))
        .verifying(s"$messagePrefix.error.not.valid.field", s => s.fold(true)(s => s.isBlank || s.trim.length > 320 || emailValidator.isValid(s)))
    )

  def oneOf(options: String*) =
    default(text, "")
      .verifying("please.select.an.option", s => options.contains(s))

}

object FormValidation extends FormValidation
