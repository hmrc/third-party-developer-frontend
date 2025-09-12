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

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.ApplicationStateHelper

object AppWorld {
  var userApplicationsOnBackend: List[ApplicationWithCollaborators] = Nil
  var tokens: Map[String, ApplicationToken]                         = Map.empty
}

class ApplicationsSteps extends ScalaDsl with EN with Matchers with NavigationSugar with CustomMatchers with ComponentTestDeveloperBuilder with FixedClock with BrowserDriver
    with ApplicationWithCollaboratorsFixtures
    with ApplicationStateHelper {

  val applicationId: ApplicationId = ApplicationId.random
  val clientId: ClientId           = ClientId("clientId")

  val collaboratorEmail: LaxEmailAddress = "john.smith@example.com".toLaxEmail

  private def defaultApp(name: String, environment: Environment) = standardApp
    .withCollaborators(Set(Collaborator(collaboratorEmail, Collaborator.Roles.ADMINISTRATOR, staticUserId)))
    .withEnvironment(environment)
    .modify(_.copy(name = ApplicationName(name)))

  Given("""^application with name '(.*)' can be created$""") { (name: String) =>
    ApplicationStub.setupApplicationNameValidation()

    val app = defaultApp(name, Environment.PRODUCTION)

    Stubs.setupPostRequest("/application", CREATED, Json.toJson(app).toString())

    ApplicationStub.setUpFetchApplication(applicationId, OK, Json.toJson(app).toString())

    configureUserApplications(app.collaborators.head.userId, List(app.withSubscriptions(Set.empty)))
  }

  Then("""^a deskpro ticket is generated with subject '(.*)'$""") { (subject: String) => ApiPlatformDeskproStub.verifyTicketCreationWithSubject(subject) }

  Given("""^I have no application assigned to my email '(.*)'$""") { (unusedEmail: String) =>
    ApplicationStub.configureUserApplications(staticUserId)
    AppWorld.userApplicationsOnBackend = Nil
  }

  def splitToSecrets(input: String): List[ClientSecret] =
    input.split(",").map(_.trim).toList.map(s => ClientSecret(ClientSecret.Id.random, s, instant))

  def configureStubsForApplications(email: LaxEmailAddress, applications: List[ApplicationWithCollaborators]): Unit = {

    ApplicationStub.configureUserApplications(staticUserId, applications.map(_.withSubscriptions(Set.empty)))
    for (app <- applications) {
      // configure to be able to fetch apps and Subscriptions
      ApplicationStub.setUpFetchApplication(app.id, OK, Json.toJson(app).toString())
      ApplicationStub.setUpFetchEmptySubscriptions(app.id, OK)
    }
  }

  When("""^I see a link to request account deletion$""") { () => driver.findElements(By.cssSelector("[id=account-deletion]")).size() shouldBe 1 }

  When("""^I click on the request account deletion link$""") { () => driver.findElement(By.cssSelector("[id=account-deletion]")).click() }

  When("""^I click on the account deletion confirmation submit button$""") { () =>
    ApiPlatformDeskproStub.setupTicketCreation()
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
    go(SubscriptionLink(id))
  }

}
