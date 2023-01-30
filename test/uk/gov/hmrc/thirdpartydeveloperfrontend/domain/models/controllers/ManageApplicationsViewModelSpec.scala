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

package uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers

import java.time.{LocalDateTime, Period, ZoneOffset}

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.AccessType
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.CollaboratorRole.DEVELOPER
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.State._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._

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
        Some(LocalDateTime.now(ZoneOffset.UTC)),
        grantLength,
        serverTokenUsed = false,
        LocalDateTime.now(ZoneOffset.UTC),
        AccessType.STANDARD,
        Environment.SANDBOX,
        Set.empty
      )

    val notYetLiveProductionApp =
      ApplicationSummary(
        ApplicationId(""),
        "",
        DEVELOPER,
        TermsOfUseStatus.AGREED,
        TESTING,
        Some(LocalDateTime.now(ZoneOffset.UTC)),
        grantLength,
        serverTokenUsed = false,
        LocalDateTime.now(ZoneOffset.UTC),
        AccessType.STANDARD,
        Environment.PRODUCTION,
        Set.empty
      )

    val liveProductionApp = notYetLiveProductionApp.copy(state = PRODUCTION)

    "return true if only sandbox apps" in {
      val model = ManageApplicationsViewModel(Seq(sandboxApp), Seq.empty, Set.empty, false, List.empty)

      model.hasNoLiveProductionApplications shouldBe true
    }

    "return true if there are sandbox apps and a not yet live production app but no live prod apps" in {
      val model = ManageApplicationsViewModel(Seq(sandboxApp), Seq(notYetLiveProductionApp), Set.empty, false, List.empty)

      model.hasNoLiveProductionApplications shouldBe true
    }

    "return false if there is a live production app" in {
      val model = ManageApplicationsViewModel(Seq(sandboxApp), Seq(notYetLiveProductionApp, liveProductionApp), Set.empty, false, List.empty)

      model.hasNoLiveProductionApplications shouldBe false
    }
  }
}
