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

package controllers

import domain.models.applications._
import org.joda.time.DateTime
import org.scalatest.{Matchers, WordSpec}
import utils._

class ApplicationSummaryTest extends WordSpec with Matchers with CollaboratorTracker with LocalUserIdTracker {

  "from" should {
    val user = "foo@bar.com".asDeveloperCollaborator

    val serverTokenApplication =
      new Application(ApplicationId(""), ClientId(""), "", DateTime.now, DateTime.now, Some(DateTime.now), Environment.PRODUCTION, collaborators = Set(user))
    val noServerTokenApplication = new Application(ApplicationId(""), ClientId(""), "", DateTime.now, DateTime.now, None, Environment.PRODUCTION, collaborators = Set(user))

    "set serverTokenUsed if SandboxApplication has a date set for lastAccessTokenUsage" in {
      val summary = SandboxApplicationSummary.from(serverTokenApplication, user.emailAddress)

      summary.serverTokenUsed should be(true)
    }

    "not set serverTokenUsed if SandboxApplication does not have a date set for lastAccessTokenUsage" in {
      val summary = SandboxApplicationSummary.from(noServerTokenApplication, user.emailAddress)

      summary.serverTokenUsed should be(false)
    }

    "set serverTokenUsed if ProductionApplication has a date set for lastAccessTokenUsage" in {
      val summary = ProductionApplicationSummary.from(serverTokenApplication, user.emailAddress)

      summary.serverTokenUsed should be(true)
    }

    "not set serverTokenUsed if ProductionApplication does not have a date set for lastAccessTokenUsage" in {
      val summary = ProductionApplicationSummary.from(noServerTokenApplication, user.emailAddress)

      summary.serverTokenUsed should be(false)
    }
  }
}
