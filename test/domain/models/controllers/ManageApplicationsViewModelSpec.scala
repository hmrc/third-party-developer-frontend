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

import domain.models.applications._
import org.joda.time.DateTime
import domain.models.apidefinitions.AccessType
import domain.models.applications.State.TESTING
import domain.models.applications.CollaboratorRole.DEVELOPER
import org.scalatest.{Matchers, WordSpec}

class ManageApplicationsViewModelSpec extends WordSpec with Matchers {
  "noProductionApplications" should {
    val sandboxApp =
      SandboxApplicationSummary(
        ApplicationId(""),
        "",
        CollaboratorRole.DEVELOPER,
        TermsOfUseStatus.AGREED,
        TESTING,
        new DateTime(),
        serverTokenUsed = false,
        new DateTime(),
        AccessType.STANDARD,
        false
      )
      
    val productionApp =
      ProductionApplicationSummary(
        ApplicationId(""),
        "",
        DEVELOPER,
        TermsOfUseStatus.AGREED,
        TESTING,
        new DateTime(),
        serverTokenUsed = false,
        new DateTime(),
        AccessType.STANDARD
      )

    "return true if only sandbox apps" in {
      val model = ManageApplicationsViewModel(Seq(sandboxApp), Seq.empty)

      model.hasNoProductionApplications shouldBe true
    }

    "return false if there is a production app" in {
      val model = ManageApplicationsViewModel(Seq(sandboxApp), Seq(productionApp))

      model.hasNoProductionApplications shouldBe false
    }
  }

}
