/*
 * Copyright 2019 HM Revenue & Customs
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

package unit.domain

import domain.Capabilities.{ChangeClientSecret, ViewCredentials}
import domain.Permissions.SandboxOrAdmin
import domain._
import org.joda.time.DateTime
import org.scalatest.{FunSpec, Matchers}

class ApplicationSpec extends FunSpec with Matchers {

  val developer = Developer("developerEmail", "DEVELOPER    ", "developerLast")
  val administrator = Developer("administratorEmail", "ADMINISTRATOR", "administratorLast")

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

    runTableTests(data, productionApplicationState)({ case (application, user) => application.allows(ViewCredentials,user, SandboxOrAdmin) })
  }

  describe("Application.isPermittedToEditAppDetails"){

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

      (Environment.SANDBOX, ROPC(), developer, false),
      (Environment.SANDBOX, ROPC(), administrator, false),
      (Environment.PRODUCTION, ROPC(), developer, false),
      (Environment.PRODUCTION, ROPC(), administrator, false),

      (Environment.SANDBOX, Privileged(), developer, false),
      (Environment.SANDBOX, Privileged(), administrator, false),
      (Environment.PRODUCTION, Privileged(), developer, false),
      (Environment.PRODUCTION, Privileged(), administrator, false)
    )

    runTableTests(data, productionApplicationState)({ case (application, user) => application.allows(ChangeClientSecret,user, SandboxOrAdmin)  })
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

  private def createApp(environment: Environment, access: Access, defaultApplicationState: ApplicationState): Application = {
    val collaborators = Set(
      Collaborator(developer.email, Role.DEVELOPER),
      Collaborator(administrator.email, Role.ADMINISTRATOR)
    )

    val app = Application("id",
      "clientId",
      "app name",
      DateTime.now(),
      DateTime.now(),
      environment,
      description = None,
      collaborators = collaborators,
      access = access,
      state = defaultApplicationState)
    app
  }

  def runTableTests(data: Seq[(Environment, Access, Developer, Boolean)], defaultApplicationState: ApplicationState)
                   (fn: (Application, Developer) => Boolean): Unit = {

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
