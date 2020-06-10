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

import play.api.data.Form
import play.api.data.Forms._
import domain.APISubscriptionStatusWithSubscriptionFields
import domain.Role
import domain.DevhubAccessLevel
import domain.ApiSubscriptionFields.SubscriptionFieldValue

object EditManageSubscription {

  case class EditApiConfigurationViewModel(
      apiName: String,
      apiVersion: String,
      apiContext: String,
      displayedStatus: String,
      fields: Seq[SubscriptionFieldViewModel],
      form: Form[EditApiConfigurationFormData],
      writableFieldValues : Map[String, FormValue])

  case class FormValue(playFormFieldNamePrefix: String, value: String)

  case class SubscriptionFieldViewModel(name: String, description: String, hint: String, canWrite: Boolean, originalValue: String)

  case class EditApiConfigurationFormData(fields: List[EditSubscriptionValueFormData])
  case class EditSubscriptionValueFormData(name: String, value: String)

  object EditApiConfigurationViewModel {
    def toViewModel(
        apiSubscription : APISubscriptionStatusWithSubscriptionFields,
        form : Form[EditApiConfigurationFormData],
        role: Role): EditApiConfigurationViewModel = {
          
    val fieldsViewModel = apiSubscription.fields.fields
        .map(field => {
          val accessLevel = DevhubAccessLevel.fromRole(role)
          val canWrite = field.definition.access.devhub.satisfiesWrite(accessLevel)
        
          SubscriptionFieldViewModel(field.definition.name, field.definition.description, field.definition.hint, canWrite, field.value)
        })
    
      val writableFormFields = fieldsViewModel.filter(_.canWrite)
        .zipWithIndex.map { case(field, index) => {
            val playFormNamePrefix = s"fields[$index]"
            val formValue = form(s"$playFormNamePrefix.value").value.getOrElse(throw new RuntimeException("Cannot find field in form values"))
            (field.name -> FormValue(playFormNamePrefix, formValue))
          }
        }.toMap

      EditApiConfigurationViewModel(
        apiSubscription.name,
        apiSubscription.apiVersion.version,
        apiSubscription.context,
        apiSubscription.apiVersion.displayedStatus,
        fieldsViewModel,
        form,
        writableFormFields)
    }
  }

  object EditApiConfigurationFormData {
    val form: Form[EditApiConfigurationFormData] = Form(
      mapping(
        "fields" -> list(
          mapping(
            "name" -> text,
            "value" -> text
          )(fromFormValues)(toFormValues)
        )
      )(EditApiConfigurationFormData.apply)(EditApiConfigurationFormData.unapply)
    )

    private def fromFormValues(name: String, value: String) = EditSubscriptionValueFormData(name, value)

    private def toFormValues(editSubscriptionValueFormData: EditSubscriptionValueFormData): Option[(String, String)] = {
      Some((editSubscriptionValueFormData.name, editSubscriptionValueFormData.value))
    }

    def toFormData(in: APISubscriptionStatusWithSubscriptionFields, role: Role): Form[EditApiConfigurationFormData] = {
      def toEditSubscriptionValueFormData(fieldValue: SubscriptionFieldValue) : EditSubscriptionValueFormData = {
        EditSubscriptionValueFormData(fieldValue.definition.name, fieldValue.value)
      }

      val writableFormFields = in.fields.fields.toList
        .filter(_.definition.access.devhub.satisfiesWrite(DevhubAccessLevel.fromRole(role)))
        .map(toEditSubscriptionValueFormData(_))
      
      EditApiConfigurationFormData.form.fill(EditApiConfigurationFormData(writableFormFields))
    }
  }
}
