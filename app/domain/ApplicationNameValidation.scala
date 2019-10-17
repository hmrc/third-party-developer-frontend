/*
 * Copyright 2019 HM Revenue & Customs
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

package domain

import controllers.FormKeys.{applicationNameAlreadyExistsKey, applicationNameInvalid2Key}

sealed class ApplicationNameValidation

case object Valid extends ApplicationNameValidation

case class Invalid(invalidName: Boolean, duplicateName: Boolean) extends ApplicationNameValidation{
  def validationErrorMessageKey: String = {
    (invalidName, duplicateName) match {
      case (true, _) => applicationNameInvalid2Key
      case _ => applicationNameAlreadyExistsKey
    }
  }
}

object Invalid {
  def invalidName = Invalid(invalidName = true, duplicateName = false)
  def duplicateName = Invalid(invalidName = false, duplicateName = true)
}