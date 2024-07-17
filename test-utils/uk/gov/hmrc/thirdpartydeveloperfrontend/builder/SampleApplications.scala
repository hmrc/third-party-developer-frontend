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

import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models.Access
import uk.gov.hmrc.apiplatform.modules.common.domain.models.Environment
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.CollaboratorTracker

trait SampleApplications extends SampleApplication {
  self: SampleDeveloperSession with CollaboratorTracker =>

  val activeApplication: Application = sampleApp

  val activeDeveloperApplication: Application = sampleApp.copy(collaborators = Set(loggedInDeveloper.email.asDeveloperCollaborator))

  val ropcApplication: Application = sampleApp.copy(access = Access.Ropc())

  val privilegedApplication: Application = sampleApp.copy(access = Access.Privileged())

  val newApplication: Application = sampleApp.copy(state = InState.testing)

  val newSandboxApplication: Application = sampleApp.copy(deployedTo = Environment.SANDBOX, state = InState.testing)

  val adminApplication: Application     = sampleApp.copy(collaborators = Set(loggedInDeveloper.email.asAdministratorCollaborator))
  val developerApplication: Application = sampleApp.copy(collaborators = Set(loggedInDeveloper.email.asDeveloperCollaborator))

  val adminSubmittedProductionApplication: Application =
    adminApplication.copy(deployedTo = Environment.PRODUCTION, state = InState.production(loggedInDeveloper.email.text, loggedInDeveloper.displayedName, ""))
  val adminCreatedProductionApplication: Application   = adminApplication.copy(deployedTo = Environment.PRODUCTION, state = InState.testing)

  val adminSubmittedSandboxApplication: Application =
    adminApplication.copy(deployedTo = Environment.SANDBOX, state = InState.production(loggedInDeveloper.email.text, loggedInDeveloper.displayedName, ""))
  val adminCreatedSandboxApplication: Application   = adminApplication.copy(deployedTo = Environment.SANDBOX, state = InState.testing)

  val developerSubmittedProductionApplication: Application =
    developerApplication.copy(deployedTo = Environment.PRODUCTION, state = InState.production(loggedInDeveloper.email.text, loggedInDeveloper.displayedName, ""))
  val developerCreatedProductionApplication: Application   = developerApplication.copy(deployedTo = Environment.PRODUCTION, state = InState.testing)

  val developerSubmittedSandboxApplication: Application =
    developerApplication.copy(deployedTo = Environment.SANDBOX, state = InState.production(loggedInDeveloper.email.text, loggedInDeveloper.displayedName, ""))
  val devloperCreatedSandboxApplication: Application    = developerApplication.copy(deployedTo = Environment.SANDBOX, state = InState.testing)

}
