/*
 * Copyright 2021 HM Revenue & Customs
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

package service

import builder._
import config.ApplicationConfig
import connectors._
import domain.models.apidefinitions._
import domain.models.applications._
import org.joda.time.DateTime
import service.SubscriptionFieldsService.SubscriptionFieldsConnector
import uk.gov.hmrc.http.{HeaderCarrier}
import utils.AsyncHmrcSpec
import domain.models.developers.UserId

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future.{failed, successful}
import service.PushPullNotificationsService.PushPullNotificationsConnector
import uk.gov.hmrc.http.UpstreamErrorResponse
import utils.LocalUserIdTracker
import domain.models.controllers.ApplicationSummary

class AppsByTeamMemberServiceSpec extends AsyncHmrcSpec with SubscriptionsBuilder with ApplicationBuilder with LocalUserIdTracker {

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
    val userId = UserId.random
    val email = "bob@example.com"
    val productionApp1 = Application(ApplicationId("id1"), ClientId("cl-id1"), "zapplication", DateTime.now, DateTime.now, None, Environment.PRODUCTION, collaborators = Set(Collaborator(email, CollaboratorRole.ADMINISTRATOR, userId)))
    val sandboxApp1 = Application(ApplicationId("id2"), ClientId("cl-id2"), "application", DateTime.now, DateTime.now, None, Environment.SANDBOX, collaborators = Set(Collaborator(email, CollaboratorRole.ADMINISTRATOR, userId)))
    val productionApp2 = Application(ApplicationId("id3"), ClientId("cl-id3"), "4pplication", DateTime.now, DateTime.now, None, Environment.PRODUCTION, collaborators = Set(Collaborator(email, CollaboratorRole.ADMINISTRATOR, userId)))

    val productionApps = Seq(productionApp1, productionApp2)
    val sandboxApps = Seq(sandboxApp1)

    implicit class SummaryImpl(application: Application) {
      def asProdSummary: ApplicationSummary = ApplicationSummary.from(application, userId)
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
