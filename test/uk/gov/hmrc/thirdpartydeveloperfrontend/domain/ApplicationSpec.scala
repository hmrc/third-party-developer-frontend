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

package uk.gov.hmrc.thirdpartydeveloperfrontend.domain

import java.time.temporal.ChronoUnit.DAYS

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models.Access
import uk.gov.hmrc.apiplatform.modules.applications.common.domain.models.FullName
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models._
import uk.gov.hmrc.apiplatform.modules.applications.submissions.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.apiplatform.modules.tpd.core.domain.models.User
import uk.gov.hmrc.apiplatform.modules.tpd.test.data.UserTestData
import uk.gov.hmrc.apiplatform.modules.tpd.test.utils.LocalUserIdTracker
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.ApplicationSyntaxes
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Capabilities.{ChangeClientSecret, ViewCredentials}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Permissions.SandboxOrAdmin
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.CollaboratorTracker

class ApplicationSpec extends AnyFunSpec with Matchers with UserTestData with LocalUserIdTracker with CollaboratorTracker with FixedClock with ApplicationSyntaxes
    with ApplicationWithCollaboratorsFixtures {

  val developer: User                     = standardDeveloper
  val developerCollaborator: Collaborator = developer.email.asDeveloperCollaborator
  val administrator: User                 = adminDeveloper

  val productionApplicationState: ApplicationState = ApplicationState(State.PRODUCTION, Some("other email"), Some("name"), Some("123"), instant)
  val testingApplicationState: ApplicationState    = ApplicationState(updatedOn = instant)
  val responsibleIndividual: ResponsibleIndividual = ResponsibleIndividual(FullName("Mr Responsible"), "ri@example.com".toLaxEmail)

  val importantSubmissionData: ImportantSubmissionData = ImportantSubmissionData(
    Some("http://example.com"),
    responsibleIndividual,
    Set(ServerLocation.InUK),
    TermsAndConditionsLocations.InDesktopSoftware,
    PrivacyPolicyLocations.InDesktopSoftware,
    List(TermsOfUseAcceptance(responsibleIndividual, instant.minus(365, DAYS), SubmissionId.random, 0))
  )

  describe("Application.canViewCredentials()") {
    val data: Seq[(Environment, Access, User, Boolean)] = Seq(
      (Environment.SANDBOX, Access.Standard(), developer, true),
      (Environment.SANDBOX, Access.Standard(), administrator, true),
      (Environment.PRODUCTION, Access.Standard(), developer, false),
      (Environment.PRODUCTION, Access.Standard(), administrator, true),
      (Environment.SANDBOX, Access.Ropc(), developer, true),
      (Environment.SANDBOX, Access.Ropc(), administrator, true),
      (Environment.PRODUCTION, Access.Ropc(), developer, false),
      (Environment.PRODUCTION, Access.Ropc(), administrator, true),
      (Environment.SANDBOX, Access.Privileged(), developer, true),
      (Environment.SANDBOX, Access.Privileged(), administrator, true),
      (Environment.PRODUCTION, Access.Privileged(), developer, false),
      (Environment.PRODUCTION, Access.Privileged(), administrator, true)
    )

    runTableTests(data, productionApplicationState)({ case (application, user) => application.allows(ViewCredentials, user, SandboxOrAdmin) })
  }

  describe("Application.isPermittedToEditAppDetails") {
    val data: Seq[(Environment, Access, User, Boolean)] = Seq(
      (Environment.SANDBOX, Access.Standard(), developer, true),
      (Environment.SANDBOX, Access.Standard(), administrator, true),
      (Environment.PRODUCTION, Access.Standard(), developer, false),
      (Environment.PRODUCTION, Access.Standard(), administrator, false),
      (Environment.SANDBOX, Access.Ropc(), developer, false),
      (Environment.SANDBOX, Access.Ropc(), administrator, false),
      (Environment.PRODUCTION, Access.Ropc(), developer, false),
      (Environment.PRODUCTION, Access.Ropc(), administrator, false),
      (Environment.SANDBOX, Access.Privileged(), developer, false),
      (Environment.SANDBOX, Access.Privileged(), administrator, false),
      (Environment.PRODUCTION, Access.Privileged(), developer, false),
      (Environment.PRODUCTION, Access.Privileged(), administrator, false)
    )

    runTableTests(data, productionApplicationState)({ case (application, user) => application.isPermittedToEditAppDetails(user) })
  }

  describe("Application.isPermittedToEditProductionAppDetails") {
    val data: Seq[(Environment, Access, User, Boolean)] = Seq(
      (Environment.SANDBOX, Access.Standard(), developer, false),
      (Environment.SANDBOX, Access.Standard(), administrator, false),
      (Environment.PRODUCTION, Access.Standard(), developer, false),
      (Environment.PRODUCTION, Access.Standard(), administrator, true),
      (Environment.SANDBOX, Access.Ropc(), developer, false),
      (Environment.SANDBOX, Access.Ropc(), administrator, false),
      (Environment.PRODUCTION, Access.Ropc(), developer, false),
      (Environment.PRODUCTION, Access.Ropc(), administrator, false),
      (Environment.SANDBOX, Access.Privileged(), developer, false),
      (Environment.SANDBOX, Access.Privileged(), administrator, false),
      (Environment.PRODUCTION, Access.Privileged(), developer, false),
      (Environment.PRODUCTION, Access.Privileged(), administrator, false)
    )

    runTableTests(data, productionApplicationState)({ case (application, user) => application.isPermittedToEditProductionAppDetails(user) })
  }

  describe("Application.isPermittedToAgreeToTermsOfUse") {
    val data: Seq[(Environment, Access, User, Boolean)] = Seq(
      (Environment.SANDBOX, Access.Standard(), developer, false),
      (Environment.SANDBOX, Access.Standard(), administrator, false),
      (Environment.PRODUCTION, Access.Standard(), developer, false),
      (Environment.PRODUCTION, Access.Standard(), administrator, true),
      (Environment.SANDBOX, Access.Ropc(), developer, false),
      (Environment.SANDBOX, Access.Ropc(), administrator, false),
      (Environment.PRODUCTION, Access.Ropc(), developer, false),
      (Environment.PRODUCTION, Access.Ropc(), administrator, false),
      (Environment.SANDBOX, Access.Privileged(), developer, false),
      (Environment.SANDBOX, Access.Privileged(), administrator, false),
      (Environment.PRODUCTION, Access.Privileged(), developer, false),
      (Environment.PRODUCTION, Access.Privileged(), administrator, false)
    )

    runTableTests(data, productionApplicationState)({ case (application, user) => application.isPermittedToAgreeToTermsOfUse(user) })
  }

  describe("Application.allows(ChangeClientSecret,user, SandboxOrAdmin)") {
    val data: Seq[(Environment, Access, User, Boolean)] = Seq(
      (Environment.SANDBOX, Access.Standard(), developer, true),
      (Environment.SANDBOX, Access.Standard(), administrator, true),
      (Environment.PRODUCTION, Access.Standard(), developer, false),
      (Environment.PRODUCTION, Access.Standard(), administrator, true),
      (Environment.SANDBOX, Access.Ropc(), developer, true),
      (Environment.SANDBOX, Access.Ropc(), administrator, true),
      (Environment.PRODUCTION, Access.Ropc(), developer, false),
      (Environment.PRODUCTION, Access.Ropc(), administrator, true),
      (Environment.SANDBOX, Access.Privileged(), developer, true),
      (Environment.SANDBOX, Access.Privileged(), administrator, true),
      (Environment.PRODUCTION, Access.Privileged(), developer, false),
      (Environment.PRODUCTION, Access.Privileged(), administrator, true)
    )

    runTableTests(data, productionApplicationState)({ case (application, user) => application.allows(ChangeClientSecret, user, SandboxOrAdmin) })
  }

  describe("Application.canViewServerToken()") {
    val data = Seq(
      (Environment.SANDBOX, Access.Standard(), developer, true),
      (Environment.SANDBOX, Access.Standard(), administrator, true),
      (Environment.PRODUCTION, Access.Standard(), developer, false),
      (Environment.PRODUCTION, Access.Standard(), administrator, true),
      (Environment.SANDBOX, Access.Ropc(), developer, false),
      (Environment.SANDBOX, Access.Ropc(), administrator, false),
      (Environment.PRODUCTION, Access.Ropc(), developer, false),
      (Environment.PRODUCTION, Access.Ropc(), administrator, false),
      (Environment.SANDBOX, Access.Privileged(), developer, false),
      (Environment.SANDBOX, Access.Privileged(), administrator, false),
      (Environment.PRODUCTION, Access.Privileged(), developer, false),
      (Environment.PRODUCTION, Access.Privileged(), administrator, false)
    )

    runTableTests(data, productionApplicationState)({ case (app, user) => app.canViewServerToken(user) })
  }

  describe("Application.canPerformApprovalProcess()") {
    val data = Seq(
      (Environment.SANDBOX, Access.Standard(), developer, false),
      (Environment.SANDBOX, Access.Standard(), administrator, false),
      (Environment.PRODUCTION, Access.Standard(), developer, false),
      (Environment.PRODUCTION, Access.Standard(), administrator, true),
      (Environment.SANDBOX, Access.Ropc(), developer, false),
      (Environment.SANDBOX, Access.Ropc(), administrator, false),
      (Environment.PRODUCTION, Access.Ropc(), developer, false),
      (Environment.PRODUCTION, Access.Ropc(), administrator, false),
      (Environment.SANDBOX, Access.Privileged(), developer, false),
      (Environment.SANDBOX, Access.Privileged(), administrator, false),
      (Environment.PRODUCTION, Access.Privileged(), developer, false),
      (Environment.PRODUCTION, Access.Privileged(), administrator, false)
    )

    runTableTests(data, testingApplicationState)({ case (app, user) => app.canPerformApprovalProcess(user) })
  }

  describe("Application.isProductionAppButEditDetailsNotAllowed()") {
    val data = Seq(
      (Environment.SANDBOX, Access.Standard(), developer, false),
      (Environment.SANDBOX, Access.Standard(), administrator, false),
      (Environment.PRODUCTION, Access.Standard(), developer, true),
      (Environment.PRODUCTION, Access.Standard(), administrator, false),
      (Environment.SANDBOX, Access.Ropc(), developer, false),
      (Environment.SANDBOX, Access.Ropc(), administrator, false),
      (Environment.PRODUCTION, Access.Ropc(), developer, false),
      (Environment.PRODUCTION, Access.Ropc(), administrator, false),
      (Environment.SANDBOX, Access.Privileged(), developer, false),
      (Environment.SANDBOX, Access.Privileged(), administrator, false),
      (Environment.PRODUCTION, Access.Privileged(), developer, false),
      (Environment.PRODUCTION, Access.Privileged(), administrator, false)
    )

    runTableTests(data, testingApplicationState)({ case (app, user) => app.isProductionAppButEditDetailsNotAllowed(user) })
  }

  describe("Application.findCollaboratorByHash()") {
    val app = createApp(Environment.PRODUCTION, Access.Standard(), productionApplicationState)

    it("should find when an email sha matches") {
      import uk.gov.hmrc.thirdpartydeveloperfrontend.helpers.string._
      app.findCollaboratorByHash(developer.email.text.toSha256) shouldBe Some(developerCollaborator)
    }

    it("should not find when an email sha doesn't match") {
      app.findCollaboratorByHash("not a matching sha") shouldBe None
    }
  }

  describe("hasResponsibleIndividual") {
    it("should return true for apps with an RI") {
      createApp(Environment.PRODUCTION, Access.Standard(importantSubmissionData = Some(importantSubmissionData)), productionApplicationState).hasResponsibleIndividual shouldBe true
    }
    it("should return false for standard apps without an RI") {
      createApp(Environment.PRODUCTION, Access.Standard(importantSubmissionData = None), productionApplicationState).hasResponsibleIndividual shouldBe false
    }
    it("should return false for non-standard apps") {
      createApp(Environment.PRODUCTION, Access.Privileged(), productionApplicationState).hasResponsibleIndividual shouldBe false
    }
  }

  private def createApp(environment: Environment, access: Access, defaultApplicationState: ApplicationState): ApplicationWithCollaborators = {

    val collaborators = Set(
      developerCollaborator,
      administrator.email.asAdministratorCollaborator
    )

    standardApp.withEnvironment(environment).withAccess(access).withState(defaultApplicationState).withCollaborators(collaborators)
  }

  def runTableTests(data: Seq[(Environment, Access, User, Boolean)], defaultApplicationState: ApplicationState)(fn: (ApplicationWithCollaborators, User) => Boolean): Unit = {

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

  private def createTestName(environment: Environment, applicationType: Access, user: User, accessAllowed: Boolean, index: Int) = {
    val padSize = 10

    f"Row ${index + 1}%2d - " +
      f"As a ${user.firstName} I expect to be ${if (accessAllowed) "ALLOWED" else "DENIED "}" +
      f" access to a ${applicationType.accessType.toString.padTo(padSize, ' ')}" +
      f" app in the ${environment.toString.padTo(padSize, ' ')} environment."
  }
}
