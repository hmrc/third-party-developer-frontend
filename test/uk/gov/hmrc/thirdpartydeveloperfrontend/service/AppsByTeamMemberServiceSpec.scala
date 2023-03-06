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

package uk.gov.hmrc.thirdpartydeveloperfrontend.service

import java.time.{LocalDateTime, Period}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.{failed, successful}

import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import uk.gov.hmrc.thirdpartydeveloperfrontend.builder._
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.ApplicationSummary
import uk.gov.hmrc.apiplatform.modules.developers.domain.models.UserId
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.PushPullNotificationsService.PushPullNotificationsConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.SubscriptionFieldsService.SubscriptionFieldsConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{AsyncHmrcSpec, LocalUserIdTracker}
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.applications.domain.models.ClientId

import uk.gov.hmrc.apiplatform.modules.applications.domain.models.ClientId
class AppsByTeamMemberServiceSpec extends AsyncHmrcSpec with SubscriptionsBuilder with ApplicationBuilder with LocalUserIdTracker {

  implicit class AppWithSubIdsSyntax(val application: Application) {
    def asAppWithSubIds(apis: ApiIdentifier*): ApplicationWithSubscriptionIds = ApplicationWithSubscriptionIds.from(application).copy(subscriptions = apis.toSet)
    def asAppWithSubIds(): ApplicationWithSubscriptionIds                     = ApplicationWithSubscriptionIds.from(application)
  }
  val versionOne = ApiVersion("1.0")
  val versionTwo = ApiVersion("2.0")

  trait Setup {
    implicit val hc: HeaderCarrier = HeaderCarrier()

    val mockProductionApplicationConnector: ThirdPartyApplicationProductionConnector =
      mock[ThirdPartyApplicationProductionConnector]

    val mockSandboxApplicationConnector: ThirdPartyApplicationSandboxConnector =
      mock[ThirdPartyApplicationSandboxConnector]

    val connectorsWrapper = new ConnectorsWrapper(
      mockSandboxApplicationConnector,
      mockProductionApplicationConnector,
      mock[SubscriptionFieldsConnector],
      mock[SubscriptionFieldsConnector],
      mock[PushPullNotificationsConnector],
      mock[PushPullNotificationsConnector],
      mock[ApplicationConfig]
    )

    val appsByTeamMemberService = new AppsByTeamMemberService(connectorsWrapper)
  }

  "Fetch by teamMember" should {
    val userId         = UserId.random
    val email          = "bob@example.com".toLaxEmail
    val grantLength    = Period.ofDays(547)
    val productionApp1 = ApplicationWithSubscriptionIds(
      ApplicationId("id1"),
      ClientId("cl-id1"),
      "zapplication",
      LocalDateTime.now,
      Some(LocalDateTime.now),
      None,
      grantLength,
      Environment.PRODUCTION,
      collaborators = Set(Collaborator(email, CollaboratorRole.ADMINISTRATOR, userId))
    )
    val sandboxApp1    = ApplicationWithSubscriptionIds(
      ApplicationId("id2"),
      ClientId("cl-id2"),
      "application",
      LocalDateTime.now,
      Some(LocalDateTime.now),
      None,
      grantLength,
      Environment.SANDBOX,
      collaborators = Set(Collaborator(email, CollaboratorRole.ADMINISTRATOR, userId))
    )
    val productionApp2 = ApplicationWithSubscriptionIds(
      ApplicationId("id3"),
      ClientId("cl-id3"),
      "4pplication",
      LocalDateTime.now,
      Some(LocalDateTime.now),
      None,
      grantLength,
      Environment.PRODUCTION,
      collaborators = Set(Collaborator(email, CollaboratorRole.ADMINISTRATOR, userId))
    )

    val productionApps = Seq(productionApp1, productionApp2)
    val sandboxApps    = Seq(sandboxApp1)

    implicit class ApplicationwithSubIdsSummarySyntax(application: ApplicationWithSubscriptionIds) {
      def asProdSummary: ApplicationSummary    = ApplicationSummary.from(application, userId)
      def asSandboxSummary: ApplicationSummary = ApplicationSummary.from(application, userId)
    }

    "sort the returned applications by name" in new Setup {
      when(mockProductionApplicationConnector.fetchByTeamMember(userId))
        .thenReturn(successful(productionApps))

      when(mockSandboxApplicationConnector.fetchByTeamMember(userId))
        .thenReturn(successful(sandboxApps))

      private val result = await(appsByTeamMemberService.fetchAllSummariesByTeamMember(userId))
      result shouldBe ((List(sandboxApp1.asSandboxSummary), List(productionApp2.asProdSummary, productionApp1.asProdSummary)))
    }

    "tolerate the sandbox connector failing with a 5xx error" in new Setup {
      when(mockProductionApplicationConnector.fetchByTeamMember(userId))
        .thenReturn(successful(productionApps))
      when(mockSandboxApplicationConnector.fetchByTeamMember(userId))
        .thenReturn(failed(UpstreamErrorResponse("Expected exception", 504, 504)))

      private val result = await(appsByTeamMemberService.fetchAllSummariesByTeamMember(userId))
      result shouldBe ((Nil, List(productionApp2.asProdSummary, productionApp1.asProdSummary)))
    }

    "not tolerate the sandbox connector failing with a 5xx error" in new Setup {
      when(mockProductionApplicationConnector.fetchByTeamMember(userId))
        .thenReturn(failed(UpstreamErrorResponse("Expected exception", 504, 504)))
      when(mockSandboxApplicationConnector.fetchByTeamMember(userId))
        .thenReturn(successful(sandboxApps))

      intercept[UpstreamErrorResponse] {
        await(appsByTeamMemberService.fetchAllSummariesByTeamMember(userId))
      }
    }
  }
}
