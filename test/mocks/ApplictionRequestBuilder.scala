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

package mocks

import controllers.ApplicationRequest
import domain.models.applications._
import domain.models.apidefinitions._
import domain.models.subscriptions._
import domain.models.developers.DeveloperSession

object Helpers {

  implicit class ApiIdentifierBuilder(context: String) {
    def %(version: String) = ApiIdentifier(ApiContext(context), ApiVersion(version))
  }

  implicit class StringTyper(string: String) {
    def fn = FieldName(string)
    def fv = FieldValue(string)
  }

  case class ApplicationRequestBuilder(
    application: Application,
    subscriptions: Set[ApiIdentifier],
    fieldValues: Map[ApiContext, Map[ApiVersion, Map[FieldName, FieldValue]]],
    developerSession: Option[DeveloperSession]
  ) {
    def toRequest[A](): ApplicationRequest[A] = {
      ???
    }
  }

  implicit class ApplicationRequestBuilderFromApplication(a: Application) {

    def asRequest: ApplicationRequestBuilder = ApplicationRequestBuilder(a, Set.empty, Map.empty, None)
  }

  implicit class ApplicationRequestPimper(ar: ApplicationRequestBuilder) {
    def subscribed(apiIdentifiers: ApiIdentifier*): ApplicationRequestBuilder = {
      ar.copy(subscriptions = ar.subscriptions ++ apiIdentifiers.toSet)
    }

    def withSession(ds: DeveloperSession): ApplicationRequestBuilder = {
      ar.copy(developerSession = Some(ds))
    }

    def withFieldValue(api: ApiIdentifier, fieldName: FieldName, fieldValue: FieldValue): ApplicationRequestBuilder = ???

    def withFieldValues(api: ApiIdentifier, fields: Map[FieldName, FieldValue]): ApplicationRequestBuilder = ???
  }


  val a : Application = ???
  val id: ApiIdentifier = "api1"%"1.0"

  val rb: ApplicationRequestBuilder = a.asRequest.subscribed(id).subscribed("api2"%"1.0", "api2"%"2.0").withFieldValue("api2"%"1.0", "bob".fn, "value1".fv)
}