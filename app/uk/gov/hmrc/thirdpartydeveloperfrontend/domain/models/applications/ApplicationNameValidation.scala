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

package uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications

import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.FieldMessageKey
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.FormKeys.{
  applicationNameAlreadyExistsKey,
  applicationNameInvalidCharactersKey,
  applicationNameInvalidKey,
  applicationNameInvalidLengthKey
}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.ApplicationNameValidationJson.ApplicationNameValidationResult

sealed trait ApplicationNameValidation

case object Valid extends ApplicationNameValidation

object ApplicationNameValidation {

  def apply(applicationNameValidationResult: ApplicationNameValidationResult): ApplicationNameValidation = {
    applicationNameValidationResult.errors match {
      case Some(errors) => Invalid(errors.invalidName, errors.duplicateName, errors.invalidLength, errors.invalidChars)
      case None         => Valid
    }
  }
}

case class Invalid(invalidName: Boolean, duplicateName: Boolean, invalidLength: Boolean, invalidChars: Boolean) extends ApplicationNameValidation {

  def validationErrorMessageKey: FieldMessageKey = {
    (invalidName, duplicateName, invalidLength, invalidChars) match {
      case (true, _, _, _) => applicationNameInvalidKey
      case (_, true, _, _) => applicationNameAlreadyExistsKey
      case (_, _, true, _) => applicationNameInvalidLengthKey
      case (_, _, _, true) => applicationNameInvalidCharactersKey
    }
  }
}

object Invalid {
  def invalidName   = Invalid(invalidName = true, duplicateName = false, invalidLength = false, invalidChars = false)
  def duplicateName = Invalid(invalidName = false, duplicateName = true, invalidLength = false, invalidChars = false)
  def invalidLength = Invalid(invalidName = false, duplicateName = false, invalidLength = true, invalidChars = false)
  def invalidChars  = Invalid(invalidName = false, duplicateName = false, invalidLength = false, invalidChars = true)
}
