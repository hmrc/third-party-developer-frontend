/*
 * Copyright 2021 HM Revenue & Customs
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

package domain.models.controllers

import domain.models.applications.ApplicationId

case class ManageApplicationsViewModel(
    sandboxApplicationSummaries: Seq[ApplicationSummary],
    productionApplicationSummaries: Seq[ApplicationSummary],
    upliftableApplicationIds: Set[ApplicationId]
) {
  lazy val hasNoProductionApplications = productionApplicationSummaries.isEmpty
  lazy val hasPriviledgedApplications = sandboxApplicationSummaries.exists(_.accessType.isPriviledged) || productionApplicationSummaries.exists(_.accessType.isPriviledged)
  lazy val sandboxApplicationsSummariesForUplift = sandboxApplicationSummaries.filter(s => upliftableApplicationIds.contains(s.id))
  lazy val countOfAppsThatCanBeUplifted = sandboxApplicationsSummariesForUplift.size
  lazy val hasAppsThatCannotBeUplifted = countOfAppsThatCanBeUplifted < sandboxApplicationSummaries.size
}
