/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.endpointauth.preconditions

import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.APIAccessType.PUBLIC
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.APIStatus.STABLE
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.{APIAccess, ApiContext, ApiIdentifier, ApiVersion}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.ApiSubscriptionFields.SubscriptionFieldDefinition
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions._

import java.time.{LocalDateTime, Period}

trait HasApplicationData extends HasApplicationState with HasApplicationEnvironment with HasApplicationCollaborators with HasApplicationAccess with HasCheckInformationOrNot {
  lazy val applicationId = ApplicationId.random
  lazy val applicationName = "my app"

  lazy val application = Application(
    applicationId, ClientId.random, applicationName, LocalDateTime.of(2020, 1, 1, 0, 0, 0), None, None, Period.ofYears(1), environment, None, collaborators, access,
    applicationState, checkInformation, IpAllowlist(false, Set.empty)
  )

  lazy val redirectUrl = "https://example.com/redirect-here"
  lazy val apiContext = ApiContext("ctx")
  lazy val apiVersion = ApiVersion("1.0")
  lazy val apiFieldName = FieldName("my_field")
  lazy val apiFieldValue = FieldValue("my value")
  lazy val apiPpnsFieldName = FieldName("my_ppns_field")
  lazy val apiPpnsFieldValue = FieldValue("my ppns value")
  lazy val appWithSubsIds = ApplicationWithSubscriptionIds.from(application)
  lazy val appWithSubsData = ApplicationWithSubscriptionData(application, Set(ApiIdentifier(apiContext, apiVersion)), Map(
    apiContext -> Map(ApiVersion("1.0") -> Map(apiFieldName -> apiFieldValue, apiPpnsFieldName -> apiPpnsFieldValue))
  ))
  lazy val subscriptionFieldDefinitions = Map(
    apiFieldName -> SubscriptionFieldDefinition(apiFieldName, "field desc", "field short desc", "hint", "STRING", AccessRequirements.Default),
    apiPpnsFieldName -> SubscriptionFieldDefinition(apiPpnsFieldName, "field desc", "field short desc", "hint", "PPNSField", AccessRequirements.Default)
  )
  lazy val allPossibleSubscriptions = Map(
    apiContext -> ApiData("service name", "api name", false, Map(apiVersion -> VersionData(STABLE, APIAccess(PUBLIC))), List(ApiCategory("category")))
  )
}
