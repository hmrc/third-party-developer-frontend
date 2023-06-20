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

import play.api.data.{Form, FormError}

object ErrorFormBuilder {

  implicit class GlobalError[T](form: Form[T]) {

    import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.FormKeys._

    // to test it
    def emailAddressAlreadyInUse =
      form.withError("submissionError", "true").withError(emailaddressField, emailalreadyInUseKey, routes.UserLoginAccount.login).withGlobalError(emailaddressAlreadyInUseGlobalKey)

    def isEmailAddressAlreadyUse: Boolean =
      form.errors(emailaddressField).flatMap(_.messages) == List(emailalreadyInUseKey)

    def firstnameGlobal() = buildGlobal(firstnameField)

    def lastnameGlobal() = buildGlobal(lastnameField)

    def emailaddressGlobal() = buildGlobal(emailaddressField)

    def passwordGlobal() = buildGlobal(passwordField)

    def currentPasswordGlobal() = buildGlobal(currentPasswordField)

    def passwordNoMatchField(): Form[T] = {
      val errors = form.globalErrors.filter(_.messages.last == passwordNoMatchGlobalKey)
      errors match {
        case _ :: Nil => form.withError(passwordField, passwordNoMatchKey).withError(confirmapasswordField, passwordNoMatchKey)
        case _        => form
      }
    }

    private def buildGlobal(field: String) = {
      val errors: Seq[FormError] = form.errors.filter(_.key == field)
      errors match {
        case s if s.nonEmpty => formKeysMap.get(s.last.message).map(form.withGlobalError(_)).getOrElse(form)
        case _               => form
      }
    }
  }

}
