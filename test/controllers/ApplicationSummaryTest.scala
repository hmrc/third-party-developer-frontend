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
import domain.models.applications.Environment.{PRODUCTION, SANDBOX}
import domain.models.controllers.ApplicationSummary

class ApplicationSummaryTest extends WordSpec with Matchers with CollaboratorTracker with LocalUserIdTracker {

  "from" should {
    val user = "foo@bar.com".asDeveloperCollaborator

    val serverTokenApplication =
      new Application(ApplicationId(""), ClientId(""), "", DateTime.now, DateTime.now, Some(DateTime.now), grantLength = 547, PRODUCTION, collaborators = Set(user))
    val noServerTokenApplication = new Application(ApplicationId(""), ClientId(""), "", DateTime.now, DateTime.now, None, grantLength = 547, PRODUCTION, collaborators = Set(user))

    "set serverTokenUsed if application has a date set for lastAccessTokenUsage" in {
      val summary = ApplicationSummary.from(serverTokenApplication.copy(deployedTo = SANDBOX), user.userId)

      summary.serverTokenUsed should be(true)
    }

    "not set serverTokenUsed if application does not have a date set for lastAccessTokenUsage" in {
      val summary = ApplicationSummary.from(noServerTokenApplication.copy(deployedTo = SANDBOX), user.userId)

      summary.serverTokenUsed should be(false)
    }
  }
}
