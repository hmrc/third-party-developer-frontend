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

case class ManageApplicationsViewModel(sandboxApplicationSummaries: Seq[SandboxApplicationSummary], productionApplicationSummaries: Seq[ProductionApplicationSummary]) {
  val hasNoProductionApplications = productionApplicationSummaries.isEmpty
  val hasPriviledgedApplications = sandboxApplicationSummaries.exists(_.accessType.isPriviledged) || productionApplicationSummaries.exists(_.accessType.isPriviledged)
  val hasAppsThatCannotBeUplifted = sandboxApplicationSummaries.exists(_.isValidTargetForUplift == false)
  lazy val sandboxApplicationsSummariesForUplift = sandboxApplicationSummaries.filter(_.isValidTargetForUplift)
  lazy val countOfAppsThatCanBeUplifted = sandboxApplicationsSummariesForUplift.size
}

object ManageApplicationsViewModel {
  def from(sandboxApplicationSummaries: Seq[SandboxApplicationSummary], productionApplicationSummaries: Seq[ProductionApplicationSummary]) : ManageApplicationsViewModel = 
    ManageApplicationsViewModel(sandboxApplicationSummaries, productionApplicationSummaries)
}