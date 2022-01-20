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

package builder

import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import utils.CollaboratorTracker

trait SampleApplications extends SampleApplication {
  self: SampleSession with CollaboratorTracker =>
    
  val activeApplication: Application = sampleApp

  val activeDeveloperApplication: Application = sampleApp.copy(collaborators = Set(loggedInDeveloper.email.asDeveloperCollaborator))

  val ropcApplication: Application = sampleApp.copy(access = ROPC())

  val privilegedApplication: Application = sampleApp.copy(access = Privileged())

  val newApplication: Application = sampleApp.copy(state = ApplicationState.testing)

  val newSandboxApplication: Application = sampleApp.copy(deployedTo = Environment.SANDBOX, state = ApplicationState.testing)

  val adminApplication: Application = sampleApp.copy(collaborators = Set(loggedInDeveloper.email.asAdministratorCollaborator))
  val developerApplication: Application = sampleApp.copy(collaborators = Set(loggedInDeveloper.email.asDeveloperCollaborator))

  val adminSubmittedProductionApplication: Application =
    adminApplication.copy(deployedTo = Environment.PRODUCTION, state = ApplicationState.production(loggedInDeveloper.email, ""))
  val adminCreatedProductionApplication: Application = adminApplication.copy(deployedTo = Environment.PRODUCTION, state = ApplicationState.testing)
  val adminSubmittedSandboxApplication: Application = adminApplication.copy(deployedTo = Environment.SANDBOX, state = ApplicationState.production(loggedInDeveloper.email, ""))
  val adminCreatedSandboxApplication: Application = adminApplication.copy(deployedTo = Environment.SANDBOX, state = ApplicationState.testing)
  val developerSubmittedProductionApplication: Application =
    developerApplication.copy(deployedTo = Environment.PRODUCTION, state = ApplicationState.production(loggedInDeveloper.email, ""))
  val developerCreatedProductionApplication: Application = developerApplication.copy(deployedTo = Environment.PRODUCTION, state = ApplicationState.testing)
  val developerSubmittedSandboxApplication: Application =
    developerApplication.copy(deployedTo = Environment.SANDBOX, state = ApplicationState.production(loggedInDeveloper.email, ""))
  val devloperCreatedSandboxApplication: Application = developerApplication.copy(deployedTo = Environment.SANDBOX, state = ApplicationState.testing)

}
