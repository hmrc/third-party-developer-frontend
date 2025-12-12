/*
 * Copyright 2025 HM Revenue & Customs
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

import ApplicationStub.configureUserApplications
import org.openqa.selenium.By

import play.api.http.Status._
import play.api.libs.json.Json
import uk.gov.hmrc.selenium.webdriver.Driver

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.common.domain.models._

object ApplicationsSteps extends NavigationSugar with ComponentTestDeveloperBuilder with ApplicationWithCollaboratorsFixtures {

  val applicationId: ApplicationId       = ApplicationId.random
  val clientId: ClientId                 = ClientId("clientId")
  val collaboratorEmail: LaxEmailAddress = "john.smith@example.com".toLaxEmail

  private def defaultApp(name: String, environment: Environment) = standardApp
    .withCollaborators(Set(Collaborator(collaboratorEmail, Collaborator.Roles.ADMINISTRATOR, staticUserId)))
    .withEnvironment(environment)
    .modify(_.copy(name = ApplicationName(name)))

  // ^application with name '(.*)' can be created$
  def givenApplicationWithNameCanBeCreated(name: String): Unit = {
    ApplicationStub.setupApplicationNameValidation()

    val app = defaultApp(name, Environment.PRODUCTION)

    Stubs.setupPostRequest("/application", CREATED, Json.toJson(app).toString())

    ApplicationStub.setUpFetchApplication(applicationId, OK, Json.toJson(app).toString())

    configureUserApplications(app.collaborators.head.userId, List(app.withSubscriptions(Set.empty)))
  }

  // ^a deskpro ticket is generated with subject '(.*)'$
  def thenADeskproTicketIsGeneratedWithSubject(subject: String): Unit = {
    ApiPlatformDeskproStub.verifyTicketCreationWithSubject(subject)
  }

  // ^I have no application assigned to my email '(.*)'$
  def givenIHaveNoApplicationAssignedToMyEmail(unusedEmail: String): Unit = {
    ApplicationStub.configureUserApplications(staticUserId)
  }

  // ^I see a link to request account deletion$
  def whenISeeALinkToRequestAccountDeletion(): Unit = {
    Driver.instance.findElements(By.cssSelector("[id=account-deletion]")).size() shouldBe 1
  }

  // ^I click on the request account deletion link$
  def whenIClickOnTheRequestAccountDeletionLink(): Unit = {
    Driver.instance.findElement(By.cssSelector("[id=account-deletion]")).click()
  }

  // ^I click on the account deletion confirmation submit button$
  def whenIClickOnTheAccountDeletionConfirmationSubmitButton(): Unit = {
    ApiPlatformDeskproStub.setupTicketCreation()
    Driver.instance.findElement(By.cssSelector("[id=submit]")).click()
  }

  // ^I select the confirmation option with id '(.*)'$
  def whenISelectTheConfirmationOptionWithId(id: String): Unit = {
    Driver.instance.findElement(By.cssSelector(s"[id=$id]")).click()
  }

  // ^I am on the unsubcribe request submitted page for application with id '(.*)' and api with name '(.*)', context '(.*)' and version '(.*)'$
  def whenIAmOnTheUnsubcribeRequestSubmittedPageForApplicationWithIdAndApiWithNameContextAndVersion(id: String, apiName: String, apiContext: String, apiVersion: String): Unit = {
    Driver.instance.getCurrentUrl shouldBe s"${EnvConfig.host}/developer/applications/$id/unsubscribe?name=$apiName&context=$apiContext&version=$apiVersion&redirectTo=MANAGE_PAGE"
  }

  // ^I am on the subscriptions page for application with id '(.*)'$
  def whenIAmOnTheSubscriptionsPageForApplicationWithId(id: String): Unit = {
    Driver.instance.getCurrentUrl shouldBe s"${EnvConfig.host}/developer/applications/$id/subscriptions"
  }

  // ^I navigate to the Subscription page for application with id '(.*)'$
  def whenINavigateToTheSubscriptionPageForApplicationWithId(id: String) = {
    go(SubscriptionLink(id))
  }

}
