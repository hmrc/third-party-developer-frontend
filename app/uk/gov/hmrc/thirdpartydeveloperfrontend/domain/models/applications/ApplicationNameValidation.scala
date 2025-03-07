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

import uk.gov.hmrc.apiplatform.modules.applications.core.interface.models.ApplicationNameValidationResult
import uk.gov.hmrc.apiplatform.modules.applications.core.interface.models.ApplicationNameValidationResult._
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.FieldMessageKey
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.FormKeys.{applicationNameAlreadyExistsKey, applicationNameInvalidKey}

sealed trait ApplicationNameValidation

case object Valid extends ApplicationNameValidation

object ApplicationNameValidation {

  def apply(applicationNameValidationResult: ApplicationNameValidationResult): ApplicationNameValidation = {
    applicationNameValidationResult match {
      case ValidApplicationName     => Valid
      case InvalidApplicationName   => Invalid(invalidName = true, duplicateName = false)
      case DuplicateApplicationName => Invalid(invalidName = false, duplicateName = true)
    }
  }
}

case class Invalid(invalidName: Boolean, duplicateName: Boolean) extends ApplicationNameValidation {

  def validationErrorMessageKey: FieldMessageKey = {
    (invalidName, duplicateName) match {
      case (true, _) => applicationNameInvalidKey
      case _         => applicationNameAlreadyExistsKey
    }
  }
}

object Invalid {
  def invalidName   = Invalid(invalidName = true, duplicateName = false)
  def duplicateName = Invalid(invalidName = false, duplicateName = true)
}
