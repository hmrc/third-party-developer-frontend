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

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.ApplicationWithCollaborators
import uk.gov.hmrc.apiplatform.modules.common.domain.models.Environment
import uk.gov.hmrc.apiplatform.modules.tpd.test.data.SampleUserSession
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.CollaboratorTracker

trait SampleApplications extends SampleApplication {
  self: SampleUserSession with CollaboratorTracker =>

  val newSandboxApplication: ApplicationWithCollaborators = sampleApp.modify(_.copy(deployedTo = Environment.SANDBOX, state = InState.testing))

  val adminApplication: ApplicationWithCollaborators = sampleApp.copy(collaborators = Set(userSession.developer.email.asAdministratorCollaborator))

  val developerApplication: ApplicationWithCollaborators = sampleApp.copy(collaborators = Set(userSession.developer.email.asDeveloperCollaborator))

  val adminSubmittedProductionApplication: ApplicationWithCollaborators =
    adminApplication.withEnvironment(Environment.PRODUCTION).withState(InState.production(userSession.developer.email.text, userSession.developer.displayedName, ""))

  val adminCreatedProductionApplication: ApplicationWithCollaborators = adminApplication.withEnvironment(Environment.PRODUCTION).withState(InState.testing)

  val adminSubmittedSandboxApplication: ApplicationWithCollaborators =
    adminApplication.withEnvironment(Environment.SANDBOX).withState(InState.production(userSession.developer.email.text, userSession.developer.displayedName, ""))

  val adminCreatedSandboxApplication: ApplicationWithCollaborators = adminApplication.withEnvironment(Environment.SANDBOX).withState(InState.testing)

  val developerSubmittedProductionApplication: ApplicationWithCollaborators =
    developerApplication.withEnvironment(Environment.SANDBOX).withState(InState.production(userSession.developer.email.text, userSession.developer.displayedName, ""))

  val developerCreatedProductionApplication: ApplicationWithCollaborators = developerApplication.withEnvironment(Environment.PRODUCTION).withState(InState.testing)

  val developerSubmittedSandboxApplication: ApplicationWithCollaborators =
    developerApplication.withEnvironment(Environment.SANDBOX).withState(InState.production(userSession.developer.email.text, userSession.developer.displayedName, ""))

  val devloperCreatedSandboxApplication: ApplicationWithCollaborators = developerApplication.withEnvironment(Environment.SANDBOX).withState(InState.testing)

}
