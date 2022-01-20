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

package uk.gov.hmrc.thirdpartydeveloperfrontend.domain

import java.time.Period

import builder.DeveloperBuilder
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Capabilities.{ChangeClientSecret, ViewCredentials}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Permissions.SandboxOrAdmin
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.Developer
import uk.gov.hmrc.thirdpartydeveloperfrontend.helpers.string._
import org.joda.time.DateTime
import utils.LocalUserIdTracker
import org.scalatest.matchers.should.Matchers
import org.scalatest.funspec.AnyFunSpec

class ApplicationSpec extends AnyFunSpec with Matchers with DeveloperBuilder with LocalUserIdTracker {

  val developer = buildDeveloper(emailAddress = "developerEmail", firstName = "DEVELOPER    ", lastName = "developerLast")
  val developerCollaborator = developer.email.asDeveloperCollaborator
  val administrator = buildDeveloper(emailAddress = "administratorEmail", firstName = "ADMINISTRATOR", lastName = "administratorLast")

  val productionApplicationState: ApplicationState = ApplicationState.production(requestedBy = "other email", verificationCode = "123")
  val testingApplicationState: ApplicationState = ApplicationState.testing

  describe("Application.canViewCredentials()") {
    val data: Seq[(Environment, Access, Developer, Boolean)] = Seq(
      (Environment.SANDBOX, Standard(), developer, true),
      (Environment.SANDBOX, Standard(), administrator, true),
      (Environment.PRODUCTION, Standard(), developer, false),
      (Environment.PRODUCTION, Standard(), administrator, true),
      (Environment.SANDBOX, ROPC(), developer, true),
      (Environment.SANDBOX, ROPC(), administrator, true),
      (Environment.PRODUCTION, ROPC(), developer, false),
      (Environment.PRODUCTION, ROPC(), administrator, true),
      (Environment.SANDBOX, Privileged(), developer, true),
      (Environment.SANDBOX, Privileged(), administrator, true),
      (Environment.PRODUCTION, Privileged(), developer, false),
      (Environment.PRODUCTION, Privileged(), administrator, true)
    )

    runTableTests(data, productionApplicationState)({ case (application, user) => application.allows(ViewCredentials, user, SandboxOrAdmin) })
  }

  describe("Application.isPermittedToEditAppDetails") {
    val data: Seq[(Environment, Access, Developer, Boolean)] = Seq(
      (Environment.SANDBOX, Standard(), developer, true),
      (Environment.SANDBOX, Standard(), administrator, true),
      (Environment.PRODUCTION, Standard(), developer, false),
      (Environment.PRODUCTION, Standard(), administrator, true),
      (Environment.SANDBOX, ROPC(), developer, false),
      (Environment.SANDBOX, ROPC(), administrator, false),
      (Environment.PRODUCTION, ROPC(), developer, false),
      (Environment.PRODUCTION, ROPC(), administrator, false),
      (Environment.SANDBOX, Privileged(), developer, false),
      (Environment.SANDBOX, Privileged(), administrator, false),
      (Environment.PRODUCTION, Privileged(), developer, false),
      (Environment.PRODUCTION, Privileged(), administrator, false)
    )

    runTableTests(data, productionApplicationState)({ case (application, user) => application.isPermittedToEditAppDetails(user) })
  }

  describe("Application.allows(ChangeClientSecret,user, SandboxOrAdmin)") {
    val data: Seq[(Environment, Access, Developer, Boolean)] = Seq(
      (Environment.SANDBOX, Standard(), developer, true),
      (Environment.SANDBOX, Standard(), administrator, true),
      (Environment.PRODUCTION, Standard(), developer, false),
      (Environment.PRODUCTION, Standard(), administrator, true),
      (Environment.SANDBOX, ROPC(), developer, true),
      (Environment.SANDBOX, ROPC(), administrator, true),
      (Environment.PRODUCTION, ROPC(), developer, false),
      (Environment.PRODUCTION, ROPC(), administrator, true),
      (Environment.SANDBOX, Privileged(), developer, true),
      (Environment.SANDBOX, Privileged(), administrator, true),
      (Environment.PRODUCTION, Privileged(), developer, false),
      (Environment.PRODUCTION, Privileged(), administrator, true)
    )

    runTableTests(data, productionApplicationState)({ case (application, user) => application.allows(ChangeClientSecret, user, SandboxOrAdmin) })
  }

  describe("Application.canViewServerToken()") {
    val data = Seq(
      (Environment.SANDBOX, Standard(), developer, true),
      (Environment.SANDBOX, Standard(), administrator, true),
      (Environment.PRODUCTION, Standard(), developer, false),
      (Environment.PRODUCTION, Standard(), administrator, true),
      (Environment.SANDBOX, ROPC(), developer, false),
      (Environment.SANDBOX, ROPC(), administrator, false),
      (Environment.PRODUCTION, ROPC(), developer, false),
      (Environment.PRODUCTION, ROPC(), administrator, false),
      (Environment.SANDBOX, Privileged(), developer, false),
      (Environment.SANDBOX, Privileged(), administrator, false),
      (Environment.PRODUCTION, Privileged(), developer, false),
      (Environment.PRODUCTION, Privileged(), administrator, false)
    )

    runTableTests(data, productionApplicationState)({ case (app, user) => app.canViewServerToken(user) })
  }

  describe("Application.canViewApprovalStatus()") {
    val data = Seq(
      (Environment.SANDBOX, Standard(), developer, false),
      (Environment.SANDBOX, Standard(), administrator, false),
      (Environment.PRODUCTION, Standard(), developer, false),
      (Environment.PRODUCTION, Standard(), administrator, true),
      (Environment.SANDBOX, ROPC(), developer, false),
      (Environment.SANDBOX, ROPC(), administrator, false),
      (Environment.PRODUCTION, ROPC(), developer, false),
      (Environment.PRODUCTION, ROPC(), administrator, false),
      (Environment.SANDBOX, Privileged(), developer, false),
      (Environment.SANDBOX, Privileged(), administrator, false),
      (Environment.PRODUCTION, Privileged(), developer, false),
      (Environment.PRODUCTION, Privileged(), administrator, false)
    )

    runTableTests(data, testingApplicationState)({ case (app, user) => app.canPerformApprovalProcess(user) })
  }

  describe("Application.findCollaboratorByHash()") {
    val app = createApp(Environment.PRODUCTION, Standard(), productionApplicationState)

    it("should find when an email sha matches") {
      app.findCollaboratorByHash(developer.email.toSha256) shouldBe Some(developerCollaborator)
    }

    it("should not find when an email sha doesn't match") {
      app.findCollaboratorByHash("not a matching sha") shouldBe None
    }
  }

  describe("Application.grantLengthDisplayValue") {
    val thousandDays = 1000
    val app = createApp(Environment.PRODUCTION, Standard(), productionApplicationState)

    it ("should return '1 month' display value for 30 days grant length") {
      app.copy(grantLength = Period.ofDays(30)).grantLengthDisplayValue() shouldBe "1 month"
    }
    it ("should return '3 months' display value for 90 days grant length")  {
      app.copy(grantLength = Period.ofDays(90)).grantLengthDisplayValue() shouldBe "3 months"
    }
    it ("should return '6 months' display value for 180 days grant length") {
      app.copy(grantLength = Period.ofDays(180)).grantLengthDisplayValue() shouldBe "6 months"
    }
    it ("should return '1 year' display value for 365 days grant length") {
      app.copy(grantLength = Period.ofDays(365)).grantLengthDisplayValue() shouldBe "1 year"
    }
    it ("should return '18 months' display value for 547 days grant length") {
      app.copy(grantLength = Period.ofDays(547)).grantLengthDisplayValue() shouldBe "18 months"
    }
    it ("should return '3 years' display value for 1095 days grant length") {
      app.copy(grantLength = Period.ofDays(1095)).grantLengthDisplayValue() shouldBe "3 years"
    }
    it ("should return '5 years' display value for 1825 days grant length") {
      app.copy(grantLength = Period.ofDays(1825)).grantLengthDisplayValue() shouldBe "5 years"
    }
    it ("should return '10 years' display value for 3650 days grant length") {
      app.copy(grantLength = Period.ofDays(3650)).grantLengthDisplayValue() shouldBe "10 years"
    }
    it ("should return '100 years' display value for 36500 days grant length") {
      app.copy(grantLength = Period.ofDays(36500)).grantLengthDisplayValue() shouldBe "100 years"
    }
    it ("should return '33 months' display value for 1000 days grant length") {
      app.copy(grantLength = Period.ofDays(thousandDays)).grantLengthDisplayValue() shouldBe "33 months"
    }
  }

  private def createApp(environment: Environment, access: Access, defaultApplicationState: ApplicationState): Application = {
    val collaborators = Set(
      developerCollaborator,
      administrator.email.asAdministratorCollaborator
    )

    val app = Application(
      ApplicationId("id"),
      ClientId("clientId"),
      "app name",
      DateTime.now(),
      DateTime.now(),
      None,
      grantLength = Period.ofDays(547),
      environment,
      description = None,
      collaborators = collaborators,
      access = access,
      state = defaultApplicationState
    )
    app
  }

  def runTableTests(data: Seq[(Environment, Access, Developer, Boolean)], defaultApplicationState: ApplicationState)(fn: (Application, Developer) => Boolean): Unit = {

    data.zipWithIndex.foreach {
      case ((environment, applicationType, user, accessAllowed), index) =>
        it(createTestName(environment, applicationType, user, accessAllowed, index)) {

          val application = createApp(environment, applicationType, defaultApplicationState)

          val result = fn(application, user)

          if (result != accessAllowed) {
            if (accessAllowed) {
              fail(s"Access was unexpectedly denied")
            } else {
              fail(s"Access was unexpectedly allowed")
            }
          }
        }
    }
  }

  private def createTestName(environment: Environment, applicationType: Access, user: Developer, accessAllowed: Boolean, index: Int) = {
    val padSize = 10

    f"Row ${index + 1}%2d - " +
      f"As a ${user.firstName} I expect to be ${if (accessAllowed) "ALLOWED" else "DENIED "}" +
      f" access to a ${applicationType.accessType.toString.padTo(padSize, ' ')}" +
      f" app in the ${environment.toString.padTo(padSize, ' ')} environment."
  }
}
