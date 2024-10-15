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
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.apiplatform.modules.tpd.test.data.SampleUserSession
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.CollaboratorTracker
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.ApplicationName

trait SampleApplication
    extends FixedClock
    with ApplicationStateHelper
    with CollaboratorTracker
    with ApplicationWithCollaboratorsFixtures {
  self: SampleUserSession =>

  val appId    = standardApp.id
  val clientId = standardApp.clientId

  val sampleApp = standardApp
    .withCollaborators(userSession.developer.email.asAdministratorCollaborator)
    .withName(ApplicationName("App name 1"))

  val testingApp   = sampleApp.withState(InState.testing)
  val submittedApp = sampleApp.withState(InState.pendingGatekeeperApproval("requestedByEmail", "requestedByName"))
}
