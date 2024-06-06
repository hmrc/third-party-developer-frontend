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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers

import play.api.data.{Form, Mapping}

object FormExtensions {

  implicit class MappingSyntax[T](mapping: Mapping[T]) {

    def verifying(error: => FieldMessageKey, constraint: (T => Boolean)): Mapping[T] = {
      mapping.verifying(error.value, constraint)
    }
  }

  implicit class FieldErrorSyntax[T](form: Form[T]) {
    def withError(fieldName: FieldNameKey, fieldMessageKey: FieldMessageKey, args: Any*) = form.withError(fieldName.value, fieldMessageKey.value, args: _*)

    def withGlobalError(fieldMessageKey: GlobalMessageKey, args: Any*) = form.withGlobalError(fieldMessageKey.value, args: _*)

    def verifying(error: => FieldMessageKey, constraint: (T => Boolean)): Mapping[T] = {
      form.mapping.verifying(error.value, constraint)
    }
  }
}

object ErrorFormBuilder {

  implicit class CommonGlobalErrorsSyntax[T](form: Form[T]) {
    import FormExtensions._
    import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.FormKeys._

    // to test it
    def emailAddressAlreadyInUse =
      form
        .withError("submissionError", "true")
        .withError(emailaddressField, emailalreadyInUseKey, routes.UserLoginAccount.login())
        .withGlobalError(emailaddressAlreadyInUseGlobalKey)

    def isEmailAddressAlreadyUse: Boolean =
      form.errors(emailaddressField.value).flatMap(_.messages) == List(emailalreadyInUseKey.value)

    def firstnameGlobal() = buildGlobal(firstnameField)

    def lastnameGlobal() = buildGlobal(lastnameField)

    def emailaddressGlobal() = buildGlobal(emailaddressField)

    def passwordGlobal() = buildGlobal(passwordField)

    def currentPasswordGlobal() = buildGlobal(currentPasswordField)

    def passwordNoMatchField(): Form[T] = {
      val errors = form.globalErrors.filter(_.messages.last == passwordNoMatchGlobalKey.value)
      errors match {
        case _ :: Nil => form
            .withError(passwordField, passwordNoMatchKey)
            .withError(confirmapasswordField, passwordNoMatchKey)
        case _        => form
      }
    }

    private def buildGlobal(field: FieldNameKey): Form[T] = {
      form.errors
        .filter(_.key == field.value)
        .lastOption
        .map(_.message)                     // w.x.y.z
        .flatMap(FormKeys.findFieldKeys(_)) // wxyz Keys
        .map(_._2)
        .map(globalKey => form.withGlobalError(globalKey))
        .getOrElse(form)
    }
  }

}
