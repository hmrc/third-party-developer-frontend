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

package domain.models.controllers

import java.time.Period
import domain.models.applications._
import org.joda.time.DateTime
import domain.models.apidefinitions.AccessType
import domain.models.applications.State.TESTING
import domain.models.applications.CollaboratorRole.DEVELOPER
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class ManageApplicationsViewModelSpec extends AnyWordSpec with Matchers {
  val grantLength = Period.ofDays(547)
  "noProductionApplications" should {
    val sandboxApp =
      ApplicationSummary(
        ApplicationId(""),
        "",
        CollaboratorRole.DEVELOPER,
        TermsOfUseStatus.AGREED,
        TESTING,
        new DateTime(),
        grantLength,
        serverTokenUsed = false,
        new DateTime(),
        AccessType.STANDARD,
        Environment.SANDBOX,
        Set.empty
      )
      
    val productionApp =
      ApplicationSummary(
        ApplicationId(""),
        "",
        DEVELOPER,
        TermsOfUseStatus.AGREED,
        TESTING,
        new DateTime(),
        grantLength,
        serverTokenUsed = false,
        new DateTime(),
        AccessType.STANDARD,
        Environment.PRODUCTION,
        Set.empty
      )

    "return true if only sandbox apps" in {
      val model = ManageApplicationsViewModel(Seq(sandboxApp), Seq.empty, Set.empty, false)

      model.hasNoProductionApplications shouldBe true
    }

    "return false if there is a production app" in {
      val model = ManageApplicationsViewModel(Seq(sandboxApp), Seq(productionApp), Set.empty, false)

      model.hasNoProductionApplications shouldBe false
    }
  }

}
