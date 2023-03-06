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

package uk.gov.hmrc.thirdpartydeveloperfrontend.builder

import java.time.{LocalDateTime, Period, ZoneOffset}

import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.CollaboratorTracker
import uk.gov.hmrc.apiplatform.modules.applications.domain.models.ClientId

trait SampleApplication {
  self: SampleSession with CollaboratorTracker =>

  val appId    = ApplicationId("myAppId")
  val clientId = ClientId("myClientId")

  val sampleApp: Application = Application(
    appId,
    clientId,
    "App name 1",
    LocalDateTime.now(ZoneOffset.UTC),
    Some(LocalDateTime.now(ZoneOffset.UTC)),
    None,
    grantLength = Period.ofDays(547),
    Environment.PRODUCTION,
    Some("Description 1"),
    Set(loggedInDeveloper.email.asAdministratorCollaborator),
    state = ApplicationState.production(loggedInDeveloper.email.text, loggedInDeveloper.displayedName, ""),
    access = Standard(redirectUris = List("https://red1", "https://red2"), termsAndConditionsUrl = Some("http://tnc-url.com"))
  )

  val testingApp   = sampleApp.copy(state = ApplicationState.testing)
  val submittedApp = sampleApp.copy(state = ApplicationState.pendingGatekeeperApproval("requestedByEmail", "requestedByName"))
}
