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

package uk.gov.hmrc.thirdpartydeveloperfrontend.testdata

import uk.gov.hmrc.apiplatform.modules.applications.domain.models.Collaborator
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.CollaboratorTracker

trait CollaboratorsTestData extends CommonTestData with CollaboratorTracker {

  val adminAsCollaborator = administratorEmail.asAdministratorCollaborator
  val developerAsCollaborator = developerEmail.asDeveloperCollaborator
  val unverifiedUserAsCollaborator = unverifiedUser.asDeveloperCollaborator
  val unverifiedAdminAsCollaborator = unverifiedUser.asAdministratorCollaborator

  val mixOfAllTypesOfCollaborators: Set[Collaborator] = Set(
    adminAsCollaborator,
    developerAsCollaborator,
    unverifiedUserAsCollaborator,
    unverifiedAdminAsCollaborator
  )

  val collaboratorsDevAndUnverifiedAdmin: Set[Collaborator] = Set(
    developerAsCollaborator,
    unverifiedAdminAsCollaborator
  )
}
