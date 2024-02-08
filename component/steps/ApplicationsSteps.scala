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

package steps

import java.util.UUID
import java.util.UUID.randomUUID

import io.cucumber.datatable.DataTable
import io.cucumber.scala.Implicits._
import io.cucumber.scala.{EN, ScalaDsl}
import matchers.CustomMatchers
import org.openqa.selenium.By
import org.scalatest.matchers.should.Matchers
import pages._
import stubs.ApplicationStub.configureUserApplications
import stubs._
import utils.{BrowserDriver, ComponentTestDeveloperBuilder}

import play.api.http.Status._
import play.api.libs.json.Json

import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models.Access
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.{ClientSecret, ClientSecretResponse, Collaborator, RedirectUri}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.ApplicationStateHelper
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.{ApplicationWithSubscriptionIds, _}

object AppWorld {
  var userApplicationsOnBackend: List[Application] = Nil
  var tokens: Map[String, ApplicationToken]        = Map.empty
}

class ApplicationsSteps extends ScalaDsl with EN with Matchers with NavigationSugar with CustomMatchers with ComponentTestDeveloperBuilder with FixedClock with BrowserDriver
    with ApplicationStateHelper {

  import java.time.Period

  val applicationId: ApplicationId = ApplicationId.random
  val clientId: ClientId           = ClientId("clientId")

  val collaboratorEmail: LaxEmailAddress = "john.smith@example.com".toLaxEmail

  private def defaultApp(name: String, environment: String) = Application(
    id = applicationId,
    clientId = clientId,
    name = name,
    createdOn = instant,
    lastAccess = Some(instant),
    lastAccessTokenUsage = None,
    Period.ofDays(547),
    Environment.apply(environment).getOrElse(Environment.PRODUCTION),
    description = None,
    collaborators = Set(Collaborator(collaboratorEmail, Collaborator.Roles.ADMINISTRATOR, staticUserId))
  )

  Given("""^application with name '(.*)' can be created$""") { (name: String) =>
    ApplicationStub.setupApplicationNameValidation()

    val app = defaultApp(name, "PRODUCTION")

    Stubs.setupPostRequest("/application", CREATED, Json.toJson(app).toString())

    ApplicationStub.setUpFetchApplication(applicationId, OK, Json.toJson(app).toString())

    configureUserApplications(app.collaborators.head.userId, List(ApplicationWithSubscriptionIds.from(app)))
  }

  Then("""^a deskpro ticket is generated with subject '(.*)'$""") { (subject: String) => DeskproStub.verifyTicketCreationWithSubject(subject) }

  Given("""^I have no application assigned to my email '(.*)'$""") { (unusedEmail: String) =>
    ApplicationStub.configureUserApplications(staticUserId)
    AppWorld.userApplicationsOnBackend = Nil
  }

  And("""^applications have the credentials:$""") { (data: DataTable) =>
    val listOfCredentials = data.asScalaRawMaps[String, String].toList
    val tuples            = listOfCredentials.map { credentials => credentials("id") -> ApplicationToken(splitToSecrets(credentials("prodClientSecrets")), credentials("prodAccessToken")) }
    AppWorld.tokens = tuples.toMap
    ApplicationStub.configureApplicationCredentials(AppWorld.tokens)
  }

  def splitToSecrets(input: String): List[ClientSecretResponse] =
    input.split(",").map(_.trim).toList.map(s => ClientSecretResponse(ClientSecret.Id.random, s, instant))

  Given("""^I have the following applications assigned to my email '(.*)':$""") { (email: LaxEmailAddress, name: String, data: DataTable) =>
    val applications = data.asScalaRawMaps[String, String].toList

    val verificationCode = "aVerificationCode"

    AppWorld.userApplicationsOnBackend = applications map { app: Map[String, String] =>
      val applicationState = app.getOrElse("state", "TESTING") match {
        case "TESTING"                        => InState.testing
        case "PRODUCTION"                     => InState.production(email.text, name, verificationCode)
        case "PENDING_GATEKEEPER_APPROVAL"    => InState.pendingGatekeeperApproval(email.text, name)
        case "PENDING_REQUESTER_VERIFICATION" => InState.pendingRequesterVerification(email.text, name, verificationCode)
        case unknownState: String             => fail(s"Unknown state '$unknownState'")
      }
      val access           = app.getOrElse("accessType", "STANDARD") match {
        case "STANDARD"   => Access.Standard(redirectUris = app.getOrElse("redirectUris", "").split(",").toList.map(_.trim).filter(_.nonEmpty).map(RedirectUri.unsafeApply))
        case "PRIVILEGED" => Access.Privileged()
        case "ROPC"       => Access.Ropc()
      }

      val environment = app.getOrElse("environment", "PRODUCTION") match {
        case "PRODUCTION" => Environment.PRODUCTION
        case "SANDBOX"    => Environment.SANDBOX
      }

      val role = app.get("role").flatMap(Collaborator.Role(_)).getOrElse(Collaborator.Roles.ADMINISTRATOR)

      Application(
        app.get("id").map(text => ApplicationId(UUID.fromString(text))).getOrElse(ApplicationId.random),
        ClientId(app.getOrElse("clientId", s"autogenerated-${randomUUID().toString}")),
        app("name"),
        instant,
        Some(instant),
        None,
        Period.ofDays(547),
        environment,
        app.get("description"),
        Set(Collaborator(email, role, UserId.random)),
        access,
        state = applicationState
      )
    }
    // configure get all apps for user email
    configureStubsForApplications(email, AppWorld.userApplicationsOnBackend)
  }

  def configureStubsForApplications(email: LaxEmailAddress, applications: List[Application]): Unit = {

    ApplicationStub.configureUserApplications(staticUserId, applications.map(ApplicationWithSubscriptionIds.from))
    for (app <- applications) {
      // configure to be able to fetch apps and Subscriptions
      ApplicationStub.setUpFetchApplication(app.id, OK, Json.toJson(app).toString())
      ApplicationStub.setUpFetchEmptySubscriptions(app.id, OK)
    }
  }

  When("""^I see a link to request account deletion$""") { () => driver.findElements(By.cssSelector("[id=account-deletion]")).size() shouldBe 1 }

  When("""^I click on the request account deletion link$""") { () => driver.findElement(By.cssSelector("[id=account-deletion]")).click() }

  When("""^I click on the account deletion confirmation submit button$""") { () =>
    DeskproStub.setupTicketCreation()
    driver.findElement(By.cssSelector("[id=submit]")).click()
  }

  When("""^I select the confirmation option with id '(.*)'$""") { (id: String) => driver.findElement(By.cssSelector(s"[id=$id]")).click() }

  When("""^I am on the unsubcribe request submitted page for application with id '(.*)' and api with name '(.*)', context '(.*)' and version '(.*)'$""") {
    (id: String, apiName: String, apiContext: String, apiVersion: String) =>
      driver.getCurrentUrl shouldBe s"${EnvConfig.host}/developer/applications/$id/unsubscribe?name=$apiName&context=$apiContext&version=$apiVersion&redirectTo=MANAGE_PAGE"
  }

  When("""^I am on the subscriptions page for application with id '(.*)'$""") { (id: String) =>
    driver.getCurrentUrl shouldBe s"${EnvConfig.host}/developer/applications/$id/subscriptions"
  }

  When("""^I navigate to the Subscription page for application with id '(.*)'$""") { id: String =>
    SubscriptionLink(id).goTo()
  }

}
