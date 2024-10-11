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

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.ApplicationWithCollaboratorsFixtures
import uk.gov.hmrc.apiplatform.modules.common.domain.models.ClientId
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.apiplatform.modules.tpd.test.data.SampleUserSession
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.CollaboratorTracker

trait SampleApplication
    extends FixedClock
    with ApplicationStateHelper
    with CollaboratorTracker
    with ApplicationWithCollaboratorsFixtures {
  self: SampleUserSession =>

  val appId    = standardApp.id
  val clientId = ClientId("myClientId")

  val sampleApp = standardApp
    .withCollaborators(userSession.developer.email.asAdministratorCollaborator)
  //   Application = Application(
  //   appId,
  //   clientId,
  //   "App name 1",
  //   instant,
  //   Some(instant),
  //   None,
  //   grantLength = Period.ofDays(547),
  //   Environment.PRODUCTION,
  //   Some("Description 1"),
  //   Set(userSession.developer.email.asAdministratorCollaborator),
  //   state = InState.production(userSession.developer.email.text, userSession.developer.displayedName, ""),
  //   access = Access.Standard(redirectUris = List("https://red1", "https://red2").map(RedirectUri.unsafeApply(_)), termsAndConditionsUrl = Some("http://tnc-url.com"))
  // )

  val testingApp   = sampleApp.withState(InState.testing)
  val submittedApp = sampleApp.withState(InState.pendingGatekeeperApproval("requestedByEmail", "requestedByName"))
}
