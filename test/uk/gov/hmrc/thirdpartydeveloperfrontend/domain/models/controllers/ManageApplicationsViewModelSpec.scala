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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models.AccessType
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.{ApplicationNameFixtures, Collaborator, GrantLength, State}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{ApplicationId, Environment, UserId}
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.apiplatform.modules.organisations.domain.models.OrganisationName
import uk.gov.hmrc.apiplatform.modules.organisations.submissions.domain.models.{OrganisationAllowList, Submission}
import uk.gov.hmrc.apiplatform.modules.organisations.submissions.utils.SubmissionsTestData
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._

class ManageApplicationsViewModelSpec extends AnyWordSpec with Matchers with FixedClock with ApplicationNameFixtures with SubmissionsTestData {

  val grantLength = GrantLength.EIGHTEEN_MONTHS

  val sandboxApp  =
    ApplicationSummary(
      ApplicationId.random,
      appNameOne,
      Collaborator.Roles.DEVELOPER,
      TermsOfUseStatus.AGREED,
      State.TESTING,
      Some(instant),
      grantLength,
      serverTokenUsed = false,
      instant,
      AccessType.STANDARD,
      Environment.SANDBOX,
      Set.empty
    )

  val notYetLiveProductionApp =
    ApplicationSummary(
      ApplicationId.random,
      appNameTwo,
      Collaborator.Roles.DEVELOPER,
      TermsOfUseStatus.AGREED,
      State.TESTING,
      Some(instant),
      grantLength,
      serverTokenUsed = false,
      instant,
      AccessType.STANDARD,
      Environment.PRODUCTION,
      Set.empty
    )

  val liveProductionApp = notYetLiveProductionApp.copy(state = State.PRODUCTION)

  val allowList = Some(OrganisationAllowList(UserId.random, OrganisationName("Test Organisation"), "requestedBy", instant))

  val answering1 = Submission.addStatusHistory(Submission.Status.Answering(instant, false))(aSubmission)
  val answering2 = Submission.updateLatestAnswersTo(samplePassAnswersToQuestions)(answering1)
  val answered   = Submission.addStatusHistory(Submission.Status.Answering(instant, true))(answering2)
  val submitted  = Submission.submit(instant, "bob@example.com")(answered)
  val declined   = Submission.decline(instant, "bob@example.com", "comments")(submitted)
  val granted    = Submission.grant(instant, "bob@example.com", Some("comments"), Some(""))(submitted)

  "noProductionApplications" should {

    "return true if only sandbox apps" in {
      val model = ManageApplicationsViewModel(Seq(sandboxApp), Seq.empty, Set.empty, false, List.empty, List.empty, None, None)

      model.hasNoLiveProductionApplications shouldBe true
    }

    "return true if there are sandbox apps and a not yet live production app but no live prod apps" in {
      val model = ManageApplicationsViewModel(Seq(sandboxApp), Seq(notYetLiveProductionApp), Set.empty, false, List.empty, List.empty, None, None)

      model.hasNoLiveProductionApplications shouldBe true
    }

    "return false if there is a live production app" in {
      val model = ManageApplicationsViewModel(Seq(sandboxApp), Seq(notYetLiveProductionApp, liveProductionApp), Set.empty, false, List.empty, List.empty, None, None)

      model.hasNoLiveProductionApplications shouldBe false
    }
  }

  "userIsAllowListed" should {

    "return false if no organisation allowlist exists" in {
      val model = ManageApplicationsViewModel(Seq(sandboxApp), Seq.empty, Set.empty, false, List.empty, List.empty, None, None)

      model.userIsAllowListed shouldBe false
    }
    "return true if organisation allowlist exists" in {
      val model = ManageApplicationsViewModel(Seq(sandboxApp), Seq.empty, Set.empty, false, List.empty, List.empty, allowList, None)

      model.userIsAllowListed shouldBe true
    }
  }

  "userHasSubmission" should {

    "return false if no organisation submissions exist" in {
      val model = ManageApplicationsViewModel(Seq(sandboxApp), Seq.empty, Set.empty, false, List.empty, List.empty, None, None)

      model.userHasSubmission shouldBe false
    }
    "return true if organisation submissions exists" in {
      val model = ManageApplicationsViewModel(Seq(sandboxApp), Seq.empty, Set.empty, false, List.empty, List.empty, None, Some(answered))

      model.userHasSubmission shouldBe true
    }
  }

  "isApproved" should {

    "return false if no organisation submissions exist" in {
      val model = ManageApplicationsViewModel(Seq(sandboxApp), Seq.empty, Set.empty, false, List.empty, List.empty, None, None)

      model.isApproved shouldBe false
    }
    "return true if approved organisation submission exists" in {
      val model = ManageApplicationsViewModel(Seq(sandboxApp), Seq.empty, Set.empty, false, List.empty, List.empty, None, Some(granted))

      model.isApproved shouldBe true
    }
  }

  "isDeclinedAndOpenToAnswers" should {

    "return false if no organisation submissions exist" in {
      val model = ManageApplicationsViewModel(Seq(sandboxApp), Seq.empty, Set.empty, false, List.empty, List.empty, None, None)

      model.isDeclinedAndOpenToAnswers shouldBe false
    }
    "return true if more than one submission instance exists and the latest is openToAnswers" in {
      val model = ManageApplicationsViewModel(Seq(sandboxApp), Seq.empty, Set.empty, false, List.empty, List.empty, None, Some(declined))

      model.isDeclinedAndOpenToAnswers shouldBe true
    }
  }

  "isNotOpenToAnswers" should {

    "return false if no organisation submissions exist" in {
      val model = ManageApplicationsViewModel(Seq(sandboxApp), Seq.empty, Set.empty, false, List.empty, List.empty, None, None)

      model.isNotOpenToAnswers shouldBe false
    }
    "return true if latest submission instance is in submitted state" in {
      val model = ManageApplicationsViewModel(Seq(sandboxApp), Seq.empty, Set.empty, false, List.empty, List.empty, None, Some(submitted))

      model.isNotOpenToAnswers shouldBe true
    }
  }
}
