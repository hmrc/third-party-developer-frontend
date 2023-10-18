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

package uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers

import org.apache.commons.lang3.StringUtils

import play.api.data.FormError

import uk.gov.hmrc.apiplatform.modules.applications.domain.models.Collaborator
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{ApiContext, ApiVersionNbr}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.{APISubscriptionStatusWithSubscriptionFields, APISubscriptionStatusWithWritableSubscriptionField}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.{DevhubAccessLevel, FieldName, FieldValue}

object EditManageSubscription {

  case class EditApiConfigurationViewModel(
      apiName: String,
      apiVersion: ApiVersionNbr,
      apiContext: ApiContext,
      displayedStatus: String,
      fields: Seq[SubscriptionFieldViewModel],
      errors: Seq[FormError]
    )

  case class EditApiConfigurationFieldViewModel(
      apiName: String,
      apiVersion: ApiVersionNbr,
      apiContext: ApiContext,
      displayedStatus: String,
      field: SubscriptionFieldViewModel,
      errors: Seq[FormError]
    )

  case class SubscriptionFieldViewModel(
      name: FieldName,
      description: String,
      hint: String,
      canWrite: Boolean,
      value: FieldValue,
      errors: Seq[FormError]
    )

  object EditApiConfigurationViewModel {

    def toViewModel(
        apiSubscription: APISubscriptionStatusWithSubscriptionFields,
        role: Collaborator.Role,
        formErrors: Seq[FormError],
        postedFormValues: Map[FieldName, FieldValue]
      ): EditApiConfigurationViewModel = {

      val fieldsViewModel = apiSubscription.fields.fields
        .map(field => {
          val accessLevel = DevhubAccessLevel.fromRole(role)
          val canWrite    = field.definition.access.devhub.satisfiesWrite(accessLevel)
          val fieldErrors = formErrors.filter(e => e.key == field.definition.name.value)

          val newValue = if (canWrite) {
            postedFormValues.getOrElse(field.definition.name, field.value)
          } else {
            field.value
          }

          SubscriptionFieldViewModel(field.definition.name, field.definition.description, field.definition.hint, canWrite, newValue, fieldErrors)
        })

      EditApiConfigurationViewModel(
        apiSubscription.name,
        apiSubscription.apiVersion.versionNbr,
        apiSubscription.context,
        apiSubscription.apiVersion.status.displayText,
        fieldsViewModel,
        formErrors
      )
    }
  }

  object EditApiConfigurationFieldViewModel {

    def toViewModel(
        apiSubscription: APISubscriptionStatusWithWritableSubscriptionField,
        role: Collaborator.Role,
        formErrors: Seq[FormError],
        postedFormValues: Map[FieldName, FieldValue]
      ): EditApiConfigurationFieldViewModel = {
      val fieldsViewModel = apiSubscription.subscriptionFieldValue.value
      val accessLevel     = DevhubAccessLevel.fromRole(role)
      val canWrite        = apiSubscription.subscriptionFieldValue.definition.access.devhub.satisfiesWrite(accessLevel)
      val fieldErrors     = formErrors.filter(e => e.key == apiSubscription.subscriptionFieldValue.definition.name.value)

      val newValue = if (canWrite) {
        postedFormValues.getOrElse(apiSubscription.subscriptionFieldValue.definition.name, fieldsViewModel)
      } else {
        fieldsViewModel
      }

      val hintText = StringUtils.firstNonBlank(
        apiSubscription.subscriptionFieldValue.definition.hint,
        apiSubscription.subscriptionFieldValue.definition.description
      )

      val subscriptionFieldViewModel =
        SubscriptionFieldViewModel(
          apiSubscription.subscriptionFieldValue.definition.name,
          apiSubscription.subscriptionFieldValue.definition.description,
          hintText,
          canWrite,
          newValue,
          fieldErrors
        )

      EditApiConfigurationFieldViewModel(
        apiSubscription.name,
        apiSubscription.apiVersion.versionNbr,
        apiSubscription.context,
        apiSubscription.apiVersion.status.displayText,
        subscriptionFieldViewModel,
        formErrors
      )
    }
  }
}
