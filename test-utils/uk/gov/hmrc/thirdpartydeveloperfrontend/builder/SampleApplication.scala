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

import uk.gov.hmrc.apiplatform.modules.common.domain.models.{ApplicationId, ClientId, Environment}
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.CollaboratorTracker

trait SampleApplication
    extends FixedClock
    with ApplicationStateHelper {
  self: SampleSession with CollaboratorTracker =>

  val appId    = ApplicationId.random
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
    state = InState.production(loggedInDeveloper.email.text, loggedInDeveloper.displayedName, ""),
    access = Standard(redirectUris = List("https://red1", "https://red2"), termsAndConditionsUrl = Some("http://tnc-url.com"))
  )

  val testingApp   = sampleApp.copy(state = InState.testing)
  val submittedApp = sampleApp.copy(state = InState.pendingGatekeeperApproval("requestedByEmail", "requestedByName"))
}
