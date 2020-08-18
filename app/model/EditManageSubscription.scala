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

package model

import domain.models.apidefinitions.{ApiContext, APISubscriptionStatusWithSubscriptionFields, ApiVersion}
import domain.models.applications.Role
import domain.models.subscriptions.DevhubAccessLevel
import play.api.data.FormError

object EditManageSubscription {

  case class EditApiConfigurationViewModel(
      apiName: String,
      apiVersion: ApiVersion,
      apiContext: ApiContext,
      displayedStatus: String,
      fields: Seq[SubscriptionFieldViewModel],
      errors: Seq[FormError]
  )

  case class SubscriptionFieldViewModel(name: String, description: String, hint: String, canWrite: Boolean, value: String, errors: Seq[FormError])

  object EditApiConfigurationViewModel {
    def toViewModel(
        apiSubscription: APISubscriptionStatusWithSubscriptionFields,
        role: Role,
        formErrors: Seq[FormError],
        postedFormValues: Map[String, String]
    ): EditApiConfigurationViewModel = {

      val fieldsViewModel = apiSubscription.fields.fields
        .map(field => {
          val accessLevel = DevhubAccessLevel.fromRole(role)
          val canWrite = field.definition.access.devhub.satisfiesWrite(accessLevel)
          val fieldErrors = formErrors.filter(e => e.key == field.definition.name)

          val newValue = if (canWrite) {
            postedFormValues.get(field.definition.name).getOrElse(field.value)
          } else {
            field.value
          }

          SubscriptionFieldViewModel(field.definition.name, field.definition.description, field.definition.hint, canWrite, newValue, fieldErrors)
        })

      EditApiConfigurationViewModel(
        apiSubscription.name,
        apiSubscription.apiVersion.version,
        apiSubscription.context,
        apiSubscription.apiVersion.displayedStatus,
        fieldsViewModel,
        formErrors
      )
    }
  }
}
