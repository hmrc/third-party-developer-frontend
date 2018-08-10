/*
 * Copyright 2018 HM Revenue & Customs
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

package component.steps

import java.util.UUID

import com.github.tomakehurst.wiremock.client.WireMock._
import component.matchers.CustomMatchers
import component.pages._
import component.stubs._
import cucumber.api.DataTable
import cucumber.api.scala.{EN, ScalaDsl}
import domain.ApiSubscriptionFields.SubscriptionField
import domain.Environment.PRODUCTION
import domain._
import org.joda.time.{DateTime, DateTimeZone}
import org.openqa.selenium.support.ui.{ExpectedConditions, WebDriverWait}
import org.openqa.selenium.{By, WebElement}
import org.scalatest.Matchers
import play.api.libs.json.Json
import steps.PageSugar
import uk.gov.hmrc.time.DateTimeUtils
import views.helper.IdFormatter

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._


object AppWorld {

  var userApplicationsOnBackend: List[Application] = Nil
  var tokens: Map[String, ApplicationTokens] = Map.empty
}

class ApplicationsSteps extends ScalaDsl with EN with Matchers with NavigationSugar with CustomMatchers with PageSugar {

  implicit val webDriver = Env.driver

  implicit class SeqAugment[T](val s: Seq[T]) {
    final def toOption: Option[Seq[T]] = if (s.isEmpty) None else Some(s)
  }

  val applicationId = "applicationId"
  val clientId = "clientId"

  private def defaultApp(name: String, environment: String) = Application(
    id = applicationId,
    clientId = clientId,
    name = name,
    DateTimeUtils.now,
    Environment.from(environment).getOrElse(PRODUCTION),
    description = None,
    collaborators = Set(Collaborator("john.smith@example.com", Role.ADMINISTRATOR))
  )

  private def approvedApp(name: String, environment: String, approvalInformation: Option[CheckInformation] = None) =
    defaultApp(name, environment).copy(checkInformation = approvalInformation)


  private val defaultProdSecret =
    ClientSecret("prod secret", "prodSecretValue", DateTime.now)

  private val defaultSandboxSecret =
    ClientSecret("sandbox secret", "sandboxSecretValue", DateTime.now)

  private val defaultTokens = ApplicationTokens(
    EnvironmentToken("prodId", Seq(defaultProdSecret), "prodAccessToken")
  )

  Given( """^I see notice that production credentials are not available in sandbox mode$""") { () =>
    val actualProductionCredentialsContents = webDriver.findElement(By.id("sandboxProductionCredsMessage")).getText
    actualProductionCredentialsContents shouldBe "Production credentials are not available on the Developer Sandbox. To obtain production credentials, please visit the live service."
  }

  Given( """^application with name '(.*)' can be created for the '(.*)' environment$""") { (name: String, environment: String) =>
    val app = defaultApp(name, environment)
    Stubs.setupPostRequest("/application", 201, Json.toJson(app).toString())
    ApplicationStub.setUpFetchApplication(applicationId, 200, Json.toJson(app).toString())
  }

  Given( """^application with name '(.*)' can be created$""") { (name: String) =>
    val app = defaultApp(name, "PRODUCTION")
    Stubs.setupPostRequest("/application", 201, Json.toJson(app).toString())
    ApplicationStub.setUpFetchApplication(applicationId, 200, Json.toJson(app).toString())
  }

  Given( """^application with name '(.*)' and id '(.*)' can be updated$""") { (name: String, id: String) =>
    Stubs.setupPostRequest(s"/application/$id", 204, "")
    Stubs.setupPostRequest(s"/application/$id/check-information", 200, "")
  }

  Given("""^I can update approval info with id '(.*)'""") { (id: String) =>
    Stubs.setupPostRequest(s"/application/$id/check-information", 204)
  }
  Given( """^the password check for '(.*)' is successful$""") { (password: String) =>
    Stubs.setupPostRequest(s"/check-password", 204, "")
  }

  Given( """^the password check for '(.*)' returns error '(.*)'$""") { (password: String, error: String) =>
    error match {
      case "locked" => Stubs.setupPostRequest(s"/check-password", 423, "")
      case "invalid" => Stubs.setupPostRequest(s"/check-password", 401, "")
      case _ => Stubs.setupPostRequest(s"/check-password", 500, "")
    }
  }

  Given( """^deskpro ticket creation is successful$""") { () =>
    DeskproStub.setupTicketCreation(200)
  }

  Given( """^deskpro ticket creation fails$""") { () =>
    DeskproStub.setupTicketCreation(502)
  }

  Given( """^the application uplift returns error '(.*)' for appId '(.*)'$""") { (error: String, appId: String) =>
    error match {
      case "alreadyExists" => Stubs.setupPostRequest(s"/application/$appId/request-uplift", 409, "")
      case "upliftConflict" => Stubs.setupPostRequest(s"/application/$appId/request-uplift", 412, "")
      case _ => Stubs.setupPostRequest(s"/application/$appId/request-uplift", 500, "")
    }
  }

  Given( """^the application uplift is successfully requested with email '(.*)' appName '(.*)' for appId '(.*)'$""") { (email: String, upliftName: String, appId: String) =>
    Stubs.setupPostRequest(s"/application/$appId/request-uplift", 204, "")
    val theAppWithId = AppWorld.userApplicationsOnBackend.find(_.id == appId)
    withClue(s"There should be exactly one application with the id '$appId'") {
      theAppWithId.nonEmpty shouldBe true
    }

    val restOfTheApps = AppWorld.userApplicationsOnBackend.filterNot(_.id == appId)

    val upliftedApp = theAppWithId.get.copy(name = upliftName, state = ApplicationState.pendingGatekeeperApproval(email))

    AppWorld.userApplicationsOnBackend = upliftedApp :: restOfTheApps
    configureStubsForApplications(email, AppWorld.userApplicationsOnBackend)
  }

  Then( """^a deskpro ticket is generated$""") { () =>
    DeskproStub.verifyTicketCreation()
  }

  Then( """^a deskpro ticket is generated with subject '(.*)'$""") { (subject: String) =>
    DeskproStub.verifyTicketCreationWithSubject(subject)
  }

  Then( """^there is a link to view subscriptions for application '(.*)', with the text '(.*)'$""") { (appName: String, linkText: String) =>
    val link = Env.driver.findElement(By.linkText(linkText))
    link.getAttribute("href") shouldBe s"${Env.host}/developer/applications/$applicationId/subscriptions"
  }

  Then( """^there is a link to submit your application for checking '(.*)', with the text '(.*)'$""") { (appName: String, linkText: String) =>
    val link = Env.driver.findElement(By.linkText(linkText))
    link.getAttribute("href") shouldBe s"${Env.host}/developer/applications/$applicationId/request-check"
  }

  Given( """^I have no application assigned to my email '(.*)'$""") { (email: String) =>
    ApplicationStub.configureUserApplications(email)
    ApplicationStub.configureUserApplications(email)
    AppWorld.userApplicationsOnBackend = Nil
  }

  And( """^applications have the credentials:$""") { (data: DataTable) =>
    val creds = data.asMaps(classOf[String], classOf[String]).asScala.toList
    val tuples = creds.map { c =>
      c.get("id") -> ApplicationTokens(
        EnvironmentToken(c.get("prodClientId"), splitToSecrets(c.get("prodClientSecrets")), c.get("prodAccessToken")))
    }
    AppWorld.tokens = tuples.toMap
    ApplicationStub.configureApplicationCredentials(AppWorld.tokens)
  }

  def splitToSecrets(input: String): Seq[ClientSecret] = {
    input.split(",").map(_.trim).map(s => ClientSecret(s, s, DateTimeUtils.now))
  }

  Given( """^I have the following applications assigned to my email '(.*)':$""") { (email: String, data: DataTable) =>
    val applications = data.asMaps(classOf[String], classOf[String]).asScala.toList

    val verificationCode = "aVerificationCode"

    AppWorld.userApplicationsOnBackend = applications map { app =>
      val applicationState = app.getOrDefault("state", "TESTING") match {
        case "TESTING" => ApplicationState.testing
        case "PRODUCTION" => ApplicationState.production(email, verificationCode)
        case "PENDING_GATEKEEPER_APPROVAL" => ApplicationState.pendingGatekeeperApproval(email)
        case "PENDING_REQUESTER_VERIFICATION" => ApplicationState.pendingRequesterVerification(email, verificationCode)
        case unknownState: String => fail(s"Unknown state '$unknownState'")
      }
      val access = app.getOrDefault("accessType", "STANDARD") match {
        case "STANDARD" => Standard(redirectUris = app.getOrDefault("redirectUris", "").split(",").toSeq.map(_.trim).filter(_.nonEmpty))
        case "PRIVILEGED" => Privileged()
        case "ROPC" => ROPC()
      }

      val environment = app.getOrDefault("environment", "PRODUCTION") match {
        case "PRODUCTION" => Environment.PRODUCTION
        case "SANDBOX" => Environment.SANDBOX
      }

      Application(app.get("id"), app.getOrElse("clientId", s"autogenerated-${UUID.randomUUID().toString}"), app.get("name"),
        DateTimeUtils.now, environment, Some(app.get("description")),
        Set(Collaborator(email, Role.withName(app.getOrDefault("role", "ADMINISTRATOR")))),
        access,
        trusted = app.getOrDefault("trusted", "false").toBoolean,
        state = applicationState)
    }
    // configure get all apps for user email
    configureStubsForApplications(email, AppWorld.userApplicationsOnBackend)
  }

  def configureStubsForApplications(email: String, applications: List[Application]) = {
    ApplicationStub.configureUserApplications(email, applications)
    for (app <- applications) {
      // configure to be able to fetch apps and Subscriptions
      ApplicationStub.setUpFetchApplication(app.id, 200, Json.toJson(app).toString())
      ApplicationStub.setUpFetchEmptySubscriptions(app.id, 200)
    }
  }

  Given( """^I am on the Add Application Success page for '(.*)'""") { name: String =>
    val app = defaultApp(name, Environment.PRODUCTION.toString)

    goOn(AddApplicationPage)
    Form.populate(scala.collection.mutable.Map(
      "applicationName" -> app.name,
      "description" -> app.description.getOrElse("")
    ))
    Stubs.setupPostRequest("/application", 201, Json.toJson(app).toString())

    webDriver.findElement(By.xpath(s"//button[text()='Add']")).click()
    ApplicationStub.setUpForAppPageFetch(app, defaultTokens)
  }

  Then( """^I am on the 'Subscription' page for '(.*)'""") { name: String =>
    val app = defaultApp(name, Environment.PRODUCTION.toString)
    on(SubscriptionPage(app.id))
  }

  Then( """^I am on the 'Details' page for app '(.*)' with id '(.*)'""") { (appId: String) =>
    on(DetailsPage(appId))
  }

  Then( """^I am on the 'Submit your application for checking' page for '(.*)'""") { appId: String =>
    on(SubmitApplicationForCheckPage(appId))
  }

  Then( """^I am on the 'Name submitted for checking' page for '(.*)'""") { appId: String =>
    on(NameSubmittedForCheckPage(appId))
  }

  Given( """^the APIs available for '(.*)' are:$""") { (appId: String, data: DataTable) =>
    val apis = data.asMaps(classOf[String], classOf[String]).asScala.toList
    val apiSubscriptions = apis.groupBy(_.get("name")).map { tuple =>
      val (name, versions) = tuple
      val apiVersions = versions.map(v => aVersionSubscription(v.get("version"), APIStatus.withName(v.get("status")),
        v.get("subscribed").toBoolean, APIAccessType.withName(v.getOrDefault("access", APIAccessType.PUBLIC.toString))))
      aApiSubscription(name, apiVersions, Some(versions.head.get("requiresTrust").toBoolean))
    }
    ApplicationStub.setUpFetchSubscriptions(appId, 200, apiSubscriptions.toSeq)
  }

  Given( """^there are no subscription fields for '(.*)' version '(.*)'""") { (apiContext: String, version: String) =>
    ApiSubscriptionFieldsStub.noSubscriptionFields(apiContext, version)
  }

  Given( """^the subscription field definitions for '(.*)' version '(.*)' are:$""" ) {
    (apiContext: String, version: String, data: DataTable) =>
    val apiFields = data.asMaps(classOf[String], classOf[String]).asScala.toList.
      map(f => SubscriptionField(f.get("name"), f.get("description"), f.get("hint"), f.get("type")))

    ApiSubscriptionFieldsStub.setUpFetchSubscriptionFieldDefinitions(apiContext, version, apiFields)
  }

  When( """^I subscribe application '(.*)' with the following subscription field values for '(.*)' version '(.*)':$""" ) { (applicationId: String, apiName: String, version: String, data: DataTable) =>
    val subFields = data.asMaps(classOf[String], classOf[String]).asScala.toList
    val clientId = AppWorld.userApplicationsOnBackend.filter(a => a.id == applicationId).head.clientId
    ApiSubscriptionFieldsStub.setUpPutFieldValues(clientId, apiName, version)
    ApplicationStub.setUpExecuteSubscription(applicationId, apiName, version, 200)
    ApplicationStub.setUpUpdateApproval(applicationId)
    ApplicationStub.setUpFetchSubscriptions(applicationId, 200, Seq(aApiSubscription(apiName, Seq(aVersionSubscription(version, APIStatus.STABLE, subscribed = true, access = APIAccessType.PUBLIC)))))

    subFields foreach { field =>
      val name = field.get("name")
      val fieldId = s"${replaceNonAlpha(apiName)}-${replaceNonAlpha(version)}-$name"

      withClue(s"no field found for $apiName version $version field $field with ID $fieldId") {
        waitForElement(By.id(fieldId)).sendKeys(field.get("value"))
      }
    }

    waitForElement(By.id(s"${IdFormatter.identifier(apiName, version)}-submit")).click()
  }

  Then( """^the following subscription field values are saved for application '(.*)' api '(.*)' version '(.*)':$""") {
    (applicationId: String, apiContext: String, apiVersion: String, data: DataTable) =>
      val clientId = AppWorld.userApplicationsOnBackend.filter(a => a.id == applicationId).head.clientId
      val fields = data.asMaps(classOf[String], classOf[String]).asScala.toList.map(f => f.get("name") -> f.get("value"))
      eventually {
        ApiSubscriptionFieldsStub.
          verifyFieldValuesSaved(clientId, apiContext, apiVersion, fields)
      }
  }

  Given( """^the next generated client secret for the application '(.*)' is '(.*)'$""") { (id: String, newSecret: String) =>
    val applicationTokens = AppWorld.tokens(id)
    val newClientSecret = ClientSecret(newSecret, newSecret, DateTime.now.withZone(DateTimeZone.getDefault))
    val newProdToken = applicationTokens.production.copy(clientSecrets = applicationTokens.production.clientSecrets :+ newClientSecret)

    ApplicationStub.configureClientSecretGeneration(id, applicationTokens.copy(newProdToken))
  }

  Given( """^the application with id '(.*)' is not subscribed to any APIs$""") { (id: String) =>
    ApplicationStub.setUpFetchEmptySubscriptions(id, 200)
  }

  def replaceNonAlpha(s: String) = s.replaceAll("\\W", "_")

  Then( """^I am presented with links to subscribe to api versions:$""") { (data: DataTable) =>
    val apis = data.asMaps(classOf[String], classOf[String]).asScala.toList
    apis foreach { api =>
      val name = replaceNonAlpha(api.get("name"))
      val version = replaceNonAlpha(api.get("version"))
      val visible = api.get("visible").toBoolean
      val link = webDriver.findElement(By.id(s"toggle-$name-$version-on"))

      withClue(s"Invalid visibility for the subscribe link for api '$name' with version '$version'") {
        link.isDisplayed shouldBe visible
      }
    }
  }

  When( """^expecting '(.*)' will be saved successfully with redirectUris:$""") { (id: String, data: DataTable) =>
    Stubs.setupPostRequest(s"/application/$id", 204)
    Stubs.setupPostRequest(s"/application/$id/check-information", 200)
    val app = AppWorld.userApplicationsOnBackend.find(_.id == id).get
    val updated = app.copy(access = app.access.asInstanceOf[Standard].copy(redirectUris = data.asList(classOf[String]).asScala))
    ApplicationStub.setUpFetchApplication(id, 200, Json.toJson(updated).toString())
  }

  When( """^expecting '(.*)' will be saved successfully with no redirectUris$""") { (id: String) =>
    Stubs.setupPostRequest(s"/application/$id", 204)
    val app = AppWorld.userApplicationsOnBackend.find(_.id == id).get
    val updated = app.copy(access = app.access.asInstanceOf[Standard].copy(redirectUris = Seq.empty))
    ApplicationStub.setUpFetchApplication(id, 200, Json.toJson(updated).toString())
  }

  Then( """^I see a save message""") { () =>
    val saveMessage: WebElement = Env.driver.findElement(By.cssSelector("[data-save-msg]"))
    saveMessage should not be null
  }

  Then( """^I see a administrator access required message""") { () =>
    val adminMessage: WebElement = Env.driver.findElement(By.cssSelector("[data-admin-required-msg]"))
    adminMessage should not be null
  }

  When( """^I delete redirect uri with index '(.*)'$""") { fieldIndex: Int =>
    val input = Env.driver.findElements(By.name("redirectUris[]")).get(fieldIndex)
    input.clear()
  }

  Then( """^I see correct number of subscriptions for each '(.*)' api:$""") { (group: String, data: DataTable) =>
    val subscriptions = data.asMaps(classOf[String], classOf[String]).asScala.toList
    subscriptions.foreach { sub =>
      eventually {
        val subRow = waitForElement(By.cssSelector(s"[data-api-subscriptions='${replaceNonAlpha(sub.get("name"))}-$group']"))
        val expectedText = sub.get("text")
        withClue(s"'${subRow.getText}' does not match with expected '$expectedText'") {
          subRow.getText == sub.get("text") shouldBe true
        }
      }
    }
  }

  Then( """^I can not see the API '(.*)'""") { apiName: String =>
    withClue(s"API '$apiName' should not be visible") {
      val api = webDriver.findElements(By.cssSelector(s"[data-api-subscriptions='${replaceNonAlpha(apiName)}']"))
      api.headOption shouldBe None
    }
  }

  Then( """^The application name '(.*)' is not editable$""") { (name: String) =>
    val element: WebElement = webDriver.findElement(By.cssSelector(s"[data-applicationName-dev]"))
    element.isDisplayed shouldBe true
    element.getTagName shouldBe "p"
  }

  Then( """^The application name '(.*)' is not visible$""") { (name: String) =>
    val element: WebElement = webDriver.findElement(By.cssSelector(s"[data-applicationName-dev]"))
    element.isDisplayed shouldBe true
    element.getTagName shouldBe "p"
  }

  When( """^I click on unsubscribe '(.*)' from API '(.*)' version '(.*)'$""") { (id: String, api: String, version: String) =>
    ApplicationStub.setUpDeleteSubscription(id, api, version, 200)
    ApplicationStub.setUpUpdateApproval(id)
    ApiSubscriptionFieldsStub.setUpDeleteSubscriptionFields(id, api, version)
    DeskproStub.setupTicketCreation()
    val button = waitForElement(By.cssSelector(s"[data-api-unsubscribe='${replaceNonAlpha(api)}-${replaceNonAlpha(version)}']"))
    button.click()
  }

  When( """^I successfully subscribe '(.*)' to API '(.*)' version '(.*)'$""") { (id: String, api: String, version: String) =>
    ApplicationStub.setUpExecuteSubscription(id, api, version, 200)
    ApplicationStub.setUpUpdateApproval(id)
    ApplicationStub.setUpFetchSubscriptions(id, 200, Seq(aApiSubscription(api, Seq(aVersionSubscription(version, APIStatus.STABLE, subscribed = true, access = APIAccessType.PUBLIC)))))
    val button = waitForElement(By.id(s"toggle-${replaceNonAlpha(api)}-${replaceNonAlpha(version)}-on"))
    button.click()
  }

  When( """^I fail to subscribe '(.*)' to API '(.*)' version '(.*)'$""") { (id: String, api: String, version: String) =>
    ApplicationStub.setUpExecuteSubscription(id, api, version, 500)
    ApplicationStub.setUpFetchSubscriptions(id, 200, Seq())
    val button = waitForElement(By.cssSelector(s"[data-api-subscribe='${replaceNonAlpha(api)}-${replaceNonAlpha(version)}']"))
    button.click()
  }

  Then( """^server error message should not be displayed on the page$""") { () =>
    assert(webDriver.findElements(By.cssSelector("span.error-notification.js-remove-error.inline-block")).size() < 1)
  }

  When( """^I do not see a button to unsubscribe from API '(.*)' version '(.*)'$""") { (api: String, version: String) =>
    webDriver.findElements(By.cssSelector(s"[data-api-unsubscribe='${replaceNonAlpha(api)}-${replaceNonAlpha(version)}']")).size() shouldBe 0
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

  When("""^I select the confirmation option with id '(.*)'$""") { (idOfDeleteButton: String ) =>
    webDriver.findElement(By.cssSelector(s"[id=$idOfDeleteButton]")).click()
  }

  When( """^I click on Unsubscribe$""") { () =>
    webDriver.findElement(By.cssSelector("[data-api-unsubscribe]")).click()
  }

  When("""^I am on the unsubcribe request submitted page for application with id '(.*)' and api with name '(.*)', context '(.*)' and version '(.*)'$""") { (id: String, apiName: String, apiContext: String, apiVersion: String) =>
    webDriver.getCurrentUrl shouldBe s"${Env.host}/developer/applications/$id/unsubscribe?name=$apiName&context=$apiContext&version=$apiVersion&redirectTo=MANAGE_PAGE"
  }

  When( """^I am on the subscriptions page for application with id '(.*)' scrolled to the API with name '(.*)' and group type '(.*)'$""") { (id: String, name: String, accessType: String) =>
    webDriver.getCurrentUrl shouldBe s"${Env.host}/developer/applications/$id/subscriptions"
  }

  When( """^I navigate to the Credentials page for application with id '(.*)'$""") { id: String =>
    go(CredentialsLink(id))
    on(CredentialsPage(id))
  }

  When( """^I navigate to the privileged or ROPC page for application with name '(.*)' and id '(.*)'""") { (name: String, id: String) =>
    go(DetailsLink(id))
    on(PrivilegedOrROPCApplicationPage(id, name))
  }

  When( """^I navigate to the Details page for application with id '(.*)'$""") { id: String =>
    go(DetailsLink(id))
    on(DetailsPage(id))
  }

  When( """^I navigate to the Edit Application page for application with id '(.*)'$""") { id: String =>
    go(EditApplicationLink(id))
    on(EditApplicationPage(id))
  }

  When( """^I navigate to the Subscription page for application with id '(.*)'$""") { id: String =>
    go(SubscriptionLink(id))
    on(SubscriptionPage(id))
  }

  When( """^I navigate to the Delete application page for application with id '(.*)'$""") { id: String =>
    go(DeleteApplicationLink(id))
    on(DeleteApplicationPage(id))
  }

  Then( """^I see a link to request application deletion$""") { () =>
    webDriver.findElements(By.linkText("Request deletion")).size() shouldBe 1
  }

  When("""^I click on the application deletion confirmation submit button$""") { () =>
    DeskproStub.setupTicketCreation()
    webDriver.findElement(By.cssSelector("[id=submit]")).click()
  }

  Then( """^I am on the privileged or ROPC page for application with name '(.*)' and id '(.*)'$""") { (name: String, id: String) =>
    on(PrivilegedOrROPCApplicationPage(id, name))
  }

  Then( """^I am on the Edit Application page for application with id '(.*)'$""") { id: String =>
    on(EditApplicationPage(id))
  }

  Then( """^I am on the Credentials page for application with id '(.*)'$""") { (id: String) =>
    on(CredentialsPage(id))
  }

  Given( """the verification '(.*)' code is not valid$""") { (verificationCode: String) =>
    Stubs.setupPostRequest(s"/verify-uplift/$verificationCode", 400)
  }

  Given( """the verification '(.*)' code is valid$""") { (verificationCode: String) =>
    Stubs.setupPostRequest(s"/verify-uplift/$verificationCode", 204)
  }


  Given( """^I navigate to the 'Application Verification' page with '(.*)'""") { verificationCode: String =>
    go(ApplicationVerificationLink(verificationCode))
  }

  Given( """^The deskpro service is invoked with content of '(.*)'$""") { (text: String) =>
    AuditStub.setupAudit(204, Some(text))
    stubFor(post(urlEqualTo("/deskpro/feedback")).withRequestBody(containing("john.smith@example.com"))
      .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""{"ticket_id":12345}""")))
  }

  Given("""^I enter an improvement suggestion of '(.*)'$""") { (text: String) =>
    textArea("improvementSuggestions").value = text
  }

  Then("""^My survey is sent to the Deskpro service with rating ([12345-]) and (no improvement suggestions|a suggestion of '.+')$""") { (rating: String, suggestion: String) =>
    val pattern = """a suggestion of '(.+)'$""".r
    suggestion match {
      case "no improvement suggestions" => DeskproStub.verifyDeskpro(rating, "n/a")
      case pattern(sug) => DeskproStub.verifyDeskpro(rating, sug)
    }
  }

  And("^cookie banner is displayed") { () =>
    webDriver.manage().deleteAllCookies()
    webDriver.navigate().refresh()
    assert(waitByWebElement(By.cssSelector("#global-cookie-message")).isDisplayed)
  }

  Given("""^I fill in the application name form with '(.+)'$""") { (name: String) =>
    val app = AppWorld.userApplicationsOnBackend.find(_.id == applicationId).getOrElse(defaultApp("TEST APP", "TESTING"))
    val updatedApp = app.copy(checkInformation = Some(CheckInformation(confirmedName = true)))

    Form.populate(scala.collection.mutable.Map(
      "applicationName" -> name
    ))

    AppWorld.userApplicationsOnBackend = AppWorld.userApplicationsOnBackend map { app =>
      if (app.id == applicationId)
        updatedApp
      else
        app
    }

    Stubs.setupPostRequest(s"/developer/applications/$applicationId/request-check/name", 303, "")
    ApplicationStub.setUpFetchApplication("applicationId", 200, Json.toJson(updatedApp).toString())
  }

  And("^application with id '(.+)' has been updated to remove name confirmation$") { (id: String) =>
    val app = AppWorld.userApplicationsOnBackend.find(_.id == id).getOrElse(defaultApp("TEST APP", "TESTING"))
    val checkInformation = app.checkInformation.get
    val updatedApp = app.copy(checkInformation = Some(checkInformation.copy(confirmedName = false)))

    AppWorld.userApplicationsOnBackend = AppWorld.userApplicationsOnBackend map { app =>
      if (app.id == id)
        updatedApp
      else
        app
    }

    ApplicationStub.setUpFetchApplication("applicationId", 200, Json.toJson(updatedApp).toString())
  }

  Given("""^I fill in the privacy policy url form with '(.+)'$""") { (url: String) =>
    val app = AppWorld.userApplicationsOnBackend.find(_.id == applicationId).getOrElse(defaultApp("TEST APP", "TESTING"))
    val checkInformation = app.checkInformation.get
    val updatedApp = app.copy(checkInformation = Some(checkInformation.copy(providedPrivacyPolicyURL = true)))

    Form.populate(scala.collection.mutable.Map(
      "privacyPolicyURL" -> url
    ))

    AppWorld.userApplicationsOnBackend = AppWorld.userApplicationsOnBackend map { app =>
      if (app.id == applicationId)
        updatedApp
      else
        app
    }

    Stubs.setupPostRequest(s"/developer/applications/$applicationId/request-check/privacy-policy", 303, "")
    ApplicationStub.setUpFetchApplication("applicationId", 200, Json.toJson(updatedApp).toString())
  }

  When("""I click on the continue button to remove from the application '(.*)', the client secrets:$""") {
    (applicationId: String, clientSecrets: DataTable) =>
      ApplicationStub.setupDeleteClientSecret(applicationId, clientSecrets.asList(classOf[String]).asScala)
      webDriver.findElement(By.cssSelector("[id=submit]")).click()

  }

  private def aVersionSubscription(version: String, apiStatus: APIStatus.Value, subscribed: Boolean, access: APIAccessType.Value) = {
    VersionSubscription(APIVersion(version, apiStatus, Some(APIAccess(access))), subscribed)
  }

  private def aApiSubscription(context: String, versions: Seq[VersionSubscription], requiresTrust: Option[Boolean] = None) = {
    APISubscription(context, s"service-$context", context, versions, requiresTrust)
  }
}
