/*
 * Copyright 2020 HM Revenue & Customs
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

import java.util.UUID.randomUUID

import matchers.CustomMatchers
import pages._
import stubs.ApplicationStub.configureUserApplications
import stubs._
import io.cucumber.datatable.DataTable
import io.cucumber.scala.Implicits._
import io.cucumber.scala.{EN, ScalaDsl}
import domain.Environment.PRODUCTION
import domain._
import org.openqa.selenium.By
import org.scalatest.Matchers
import play.api.http.Status._
import play.api.libs.json.Json
import uk.gov.hmrc.time.DateTimeUtils

import scala.collection.JavaConverters._


object AppWorld {

  var userApplicationsOnBackend: List[Application] = Nil
  var tokens: Map[String, ApplicationToken] = Map.empty
}

class ApplicationsSteps extends ScalaDsl with EN with Matchers with NavigationSugar with CustomMatchers with PageSugar {

  implicit val webDriver = Env.driver

  val applicationId = "applicationId"
  val clientId = "clientId"

  private def defaultApp(name: String, environment: String) = Application(
    id = applicationId,
    clientId = clientId,
    name = name,
    createdOn = DateTimeUtils.now,
    lastAccess = DateTimeUtils.now,
    lastAccessTokenUsage = None,
    Environment.from(environment).getOrElse(PRODUCTION),
    description = None,
    collaborators = Set(Collaborator("john.smith@example.com", Role.ADMINISTRATOR))
  )

  Given( """^application with name '(.*)' can be created$""") { (name: String) =>
    ApplicationStub.setupApplicationNameValidation()

    val app = defaultApp(name, "PRODUCTION")

    Stubs.setupPostRequest("/application", CREATED, Json.toJson(app).toString())

    ApplicationStub.setUpFetchApplication(applicationId, OK, Json.toJson(app).toString())

    configureUserApplications(app.collaborators.head.emailAddress, List(app))
  }

  Then( """^a deskpro ticket is generated with subject '(.*)'$""") { (subject: String) =>
    DeskproStub.verifyTicketCreationWithSubject(subject)
  }

  Then( """^there is a link to submit your application for checking with the text '(.*)'$""") { (linkText: String) =>
    val link = Env.driver.findElement(By.linkText(linkText))
    link.getAttribute("href") shouldBe s"${Env.host}/developer/applications/$applicationId/request-check"
  }

  Given( """^I have no application assigned to my email '(.*)'$""") { (email: String) =>
    ApplicationStub.configureUserApplications(email)
    ApplicationStub.configureUserApplications(email)
    AppWorld.userApplicationsOnBackend = Nil
  }

  And( """^applications have the credentials:$""") { (data: DataTable) =>
    val listOfCredentials = data.asScalaRawMaps[String, String].toList
    val tuples = listOfCredentials.map { credentials =>
      credentials("id") -> ApplicationToken(credentials("prodClientId"), splitToSecrets(credentials("prodClientSecrets")), credentials("prodAccessToken"))
    }
    AppWorld.tokens = tuples.toMap
    ApplicationStub.configureApplicationCredentials(AppWorld.tokens)
  }

  def splitToSecrets(input: String): Seq[ClientSecret] = input.split(",").map(_.trim).map(s => ClientSecret(randomUUID.toString, s, DateTimeUtils.now))

  Given( """^I have the following applications assigned to my email '(.*)':$""") { (email: String, data: DataTable) =>
    val applications = data.asScalaRawMaps[String, String].toList

    val verificationCode = "aVerificationCode"

    AppWorld.userApplicationsOnBackend = applications map { app: Map[String, String] =>
      val applicationState = app.getOrElse("state", "TESTING") match {
        case "TESTING" => ApplicationState.testing
        case "PRODUCTION" => ApplicationState.production(email, verificationCode)
        case "PENDING_GATEKEEPER_APPROVAL" => ApplicationState.pendingGatekeeperApproval(email)
        case "PENDING_REQUESTER_VERIFICATION" => ApplicationState.pendingRequesterVerification(email, verificationCode)
        case unknownState: String => fail(s"Unknown state '$unknownState'")
      }
      val access = app.getOrElse("accessType", "STANDARD") match {
        case "STANDARD" => Standard(redirectUris = app.getOrElse("redirectUris", "").split(",").toSeq.map(_.trim).filter(_.nonEmpty))
        case "PRIVILEGED" => Privileged()
        case "ROPC" => ROPC()
      }

      val environment = app.getOrElse("environment", "PRODUCTION") match {
        case "PRODUCTION" => Environment.PRODUCTION
        case "SANDBOX" => Environment.SANDBOX
      }

      Application(app("id"), app.getOrElse("clientId", s"autogenerated-${randomUUID().toString}"), app("name"),
        DateTimeUtils.now, DateTimeUtils.now, None, environment, app.get("description"),
        Set(Collaborator(email, Role.withName(app.getOrElse("role", "ADMINISTRATOR")))),
        access,
        state = applicationState)
    }
    // configure get all apps for user email
    configureStubsForApplications(email, AppWorld.userApplicationsOnBackend)
  }

  def configureStubsForApplications(email: String, applications: List[Application]) = {
    ApplicationStub.configureUserApplications(email, applications)
    for (app <- applications) {
      // configure to be able to fetch apps and Subscriptions
      ApplicationStub.setUpFetchApplication(app.id,OK, Json.toJson(app).toString())
      ApplicationStub.setUpFetchEmptySubscriptions(app.id, OK)
    }
  }

  When( """^I see a link to request account deletion$""") { () =>
    webDriver.findElements(By.cssSelector("[id=account-deletion]")).size() shouldBe 1
  }

  When("""^I click on the request account deletion link$""") { () =>
    webDriver.findElement(By.cssSelector("[id=account-deletion]")).click()
  }

  When("""^I click on the account deletion confirmation submit button$""") { () =>
    DeskproStub.setupTicketCreation()
    webDriver.findElement(By.cssSelector("[id=submit]")).click()
  }

  When("""^I select the confirmation option with id '(.*)'$""") { (id: String ) =>
    webDriver.findElement(By.cssSelector(s"[id=$id]")).click()
  }

  When("""^I am on the unsubcribe request submitted page for application with id '(.*)' and api with name '(.*)', context '(.*)' and version '(.*)'$""") { (id: String, apiName: String, apiContext: String, apiVersion: String) =>
    webDriver.getCurrentUrl shouldBe s"${Env.host}/developer/applications/$id/unsubscribe?name=$apiName&context=$apiContext&version=$apiVersion&redirectTo=MANAGE_PAGE"
  }

  When( """^I am on the subscriptions page for application with id '(.*)'$""") { (id: String) =>
    webDriver.getCurrentUrl shouldBe s"${Env.host}/developer/applications/$id/subscriptions"
  }

  When( """^I navigate to the Subscription page for application with id '(.*)'$""") { id: String =>
    go(SubscriptionLink(id))
    on(SubscriptionPage(id))
  }

  private def aVersionSubscription(version: String, apiStatus: APIStatus, subscribed: Boolean, access: APIAccessType) = {
    VersionSubscription(APIVersion(version, apiStatus, Some(APIAccess(access))), subscribed)
  }

  private def aApiSubscription(context: String, versions: Seq[VersionSubscription], requiresTrust: Option[Boolean] = None) = {
    APISubscription(context, s"service-$context", context, versions, requiresTrust)
  }
}
