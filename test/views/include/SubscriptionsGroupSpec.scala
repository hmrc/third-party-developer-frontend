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

package views.include

import org.jsoup.Jsoup
import org.jsoup.nodes.{Document, Element}
import views.helper.CommonViewSpec
import views.html.include.SubscriptionsGroup

import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest

import uk.gov.hmrc.apiplatform.modules.apis.domain.models._
import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models.Access
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.{Collaborator, _}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{ApiContext, ApiVersionNbr, ApplicationId, ClientId, Environment}
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.apiplatform.modules.tpd.sessions.domain.models.{DeveloperSession, LoggedInState}
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.{DeveloperBuilder, DeveloperSessionBuilder, SubscriptionsBuilder}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.APISubscriptions
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions.ApiSubscriptionFields
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.views.SubscriptionRedirect
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{CollaboratorTracker, LocalUserIdTracker, WithCSRFAddToken}

class SubscriptionsGroupSpec
    extends CommonViewSpec
    with WithCSRFAddToken
    with SubscriptionsBuilder
    with CollaboratorTracker
    with LocalUserIdTracker
    with DeveloperSessionBuilder
    with DeveloperBuilder
    with FixedClock {

  implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withCSRFToken

  val loggedInDeveloper: DeveloperSession = buildDeveloper("givenname.familyname@example.com".toLaxEmail, "Givenname", "Familyname").loggedIn
  val applicationId: ApplicationId        = ApplicationId.random
  val clientId: ClientId                  = ClientId("clientId123")
  val applicationName                     = "Test Application"
  val apiName                             = "Test API"
  val apiContext: ApiContext              = ApiContext("test")
  val apiVersion: ApiVersionNbr           = ApiVersionNbr("1.0")

  val emptyFields: ApiSubscriptionFields.SubscriptionFieldsWrapper = emptySubscriptionFieldsWrapper(applicationId, clientId, apiContext, apiVersion)

  val subscriptionStatus: APISubscriptionStatus =
    APISubscriptionStatus(apiName, ServiceName(apiName), apiContext, ApiVersion(apiVersion, ApiStatus.STABLE, ApiAccess.PUBLIC, List.empty), false, false, fields = emptyFields)

  val apiSubscriptions: Seq[APISubscriptions] = Seq(APISubscriptions(apiName, ServiceName(apiName), apiContext, Seq(subscriptionStatus)))

  val subscriptionsGroup: SubscriptionsGroup = app.injector.instanceOf[SubscriptionsGroup]

  case class Page(role: Collaborator.Role, environment: Environment, state: ApplicationState) {

    lazy val body: Document = {
      val application = Application(
        applicationId,
        clientId,
        applicationName,
        instant,
        Some(instant),
        None,
        grantLength,
        environment,
        Some("Description 1"),
        Set(loggedInDeveloper.email.asCollaborator(role)),
        state = state,
        access = Access.Standard(
          redirectUris = List("https://red1.example.com", "https://red2.example.con").map(RedirectUri.unsafeApply),
          termsAndConditionsUrl = Some("http://tnc-url.example.com")
        )
      )

      Jsoup.parse(
        subscriptionsGroup
          .render(
            role,
            application,
            apiSubscriptions,
            group = "Example",
            afterSubscriptionRedirectTo = SubscriptionRedirect.MANAGE_PAGE,
            showSubscriptionFields = true,
            messagesProvider,
            appConfig,
            request
          )
          .body
      )
    }

    lazy val toggle: Element                    = body.getElementById("test-1_0-toggle")
    lazy val requestChangeLink: Option[Element] = Option(body.getElementsByClass("request-change-link").first)
  }

  "subscriptionsGroup" when {
    val productionState                   = ApplicationState(State.PRODUCTION, Some(loggedInDeveloper.email.text), Some(loggedInDeveloper.displayedName), Some(""), instant)
    val pendingGatekeeperApprovalState    =
      ApplicationState(State.PENDING_GATEKEEPER_APPROVAL, Some(loggedInDeveloper.email.text), Some(loggedInDeveloper.displayedName), Some(""), instant)
    val pendingRequesterVerificationState =
      ApplicationState(State.PENDING_REQUESTER_VERIFICATION, Some(loggedInDeveloper.email.text), Some(loggedInDeveloper.displayedName), Some(""), instant)

    "logged in as a developer" should {
      val role = Collaborator.Roles.DEVELOPER

      "render enabled toggles for a sandbox app" in {
        val page = Page(role, Environment.SANDBOX, productionState)

        page.toggle.hasAttr("disabled") shouldBe false
        page.requestChangeLink shouldBe None
      }

      "render enabled toggles for a created production app" in {
        val page = Page(role, Environment.PRODUCTION, ApplicationState(updatedOn = instant))

        page.toggle.hasAttr("disabled") shouldBe false
        page.requestChangeLink shouldBe None
      }

      "render disabled toggles for a pending-gatekeeper-approval production app with no link to request change" in {
        val page = Page(role, Environment.PRODUCTION, pendingGatekeeperApprovalState)

        page.toggle.hasAttr("disabled") shouldBe true
        page.requestChangeLink shouldBe None
      }

      "render disabled toggles for a pending-requester-verification production app with no link to request change" in {
        val page = Page(role, Environment.PRODUCTION, pendingRequesterVerificationState)

        page.toggle.hasAttr("disabled") shouldBe true
        page.requestChangeLink shouldBe None
      }

      "render disabled toggles for a checked production app with no link to request change" in {
        val page = Page(role, Environment.PRODUCTION, productionState)

        page.toggle.hasAttr("disabled") shouldBe true
        page.requestChangeLink shouldBe None
      }
    }

    "logged in as an administrator" should {
      val role = Collaborator.Roles.ADMINISTRATOR

      "render enabled toggles for a sandbox app" in {
        val page = Page(role, Environment.SANDBOX, productionState)

        page.toggle.hasAttr("disabled") shouldBe false
        page.requestChangeLink shouldBe None
      }

      "render enabled toggles for a created production app" in {
        val page = Page(role, Environment.PRODUCTION, ApplicationState(updatedOn = instant))

        page.toggle.hasAttr("disabled") shouldBe false
        page.requestChangeLink shouldBe None
      }

      "render disabled toggles for a pending-gatekeeper-approval production app with a link to request change" in {
        val page = Page(role, Environment.PRODUCTION, pendingGatekeeperApprovalState)

        page.toggle.hasAttr("disabled") shouldBe true
        page.requestChangeLink.isDefined shouldBe true
      }

      "render disabled toggles for a pending-requester-verification production app with a link to request change" in {
        val page = Page(role, Environment.PRODUCTION, pendingRequesterVerificationState)

        page.toggle.hasAttr("disabled") shouldBe true
        page.requestChangeLink.isDefined shouldBe true
      }

      "render disabled toggles for a checked production app with a link to request change" in {
        val page = Page(role, Environment.PRODUCTION, productionState)

        page.toggle.hasAttr("disabled") shouldBe true
        page.requestChangeLink.isDefined shouldBe true
      }
    }
  }
}
