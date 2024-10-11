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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.ApplicationWithCollaboratorsFixtures
import uk.gov.hmrc.apiplatform.modules.common.domain.models.Environment
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.ApplicationSummary
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils._

class ApplicationSummaryTest extends AnyWordSpec with Matchers with CollaboratorTracker with LocalUserIdTracker with FixedClock with ApplicationWithCollaboratorsFixtures {

  "from" should {
    val user = "foo@bar.com".toLaxEmail.asDeveloperCollaborator

    val serverTokenApplication   = standardApp.modify(_.copy(createdOn = instant, lastAccess = Some(instant), lastAccessTokenUsage = Some(instant))).withCollaborators(user)
    val noServerTokenApplication = standardApp.modify(_.copy(createdOn = instant, lastAccess = Some(instant), lastAccessTokenUsage = None)).withCollaborators(user)

    "set serverTokenUsed if application has a date set for lastAccessTokenUsage" in {
      val summary = ApplicationSummary.from(serverTokenApplication.withEnvironment(Environment.SANDBOX), user.userId)

      summary.serverTokenUsed shouldBe (true)
    }

    "not set serverTokenUsed if application does not have a date set for lastAccessTokenUsage" in {
      val summary = ApplicationSummary.from(noServerTokenApplication.withEnvironment(Environment.SANDBOX), user.userId)

      summary.serverTokenUsed shouldBe (false)
    }
  }
}
