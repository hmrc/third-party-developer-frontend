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

package uk.gov.hmrc.thirdpartydeveloperfrontend.mocks.service

import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}

import scala.concurrent.Future.successful
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.UserId
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.ApplicationSummary
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.AppsByTeamMemberService

trait AppsByTeamMemberServiceMock extends MockitoSugar with ArgumentMatchersSugar {
  val appsByTeamMemberServiceMock = mock[AppsByTeamMemberService]

  def fetchProductionSummariesByAdmin(userId: UserId, apps: Seq[ApplicationWithSubscriptionIds]) = {
    when(appsByTeamMemberServiceMock.fetchProductionSummariesByAdmin(eqTo(userId))(*)).thenReturn(successful(apps))
  }

  def fetchAppsByTeamMemberReturns(environment: Environment)(apps: Seq[ApplicationWithSubscriptionIds]) = 
    when(appsByTeamMemberServiceMock.fetchAppsByTeamMember(eqTo(environment))(*[UserId])(*))
    .thenReturn(successful(apps.map(_.copy(deployedTo = environment))))
    
  def fetchByTeamMembersWithRoleReturns(apps: Seq[ApplicationWithSubscriptionIds]) = 
    when(appsByTeamMemberServiceMock.fetchByTeamMemberWithRole(*)(*)(*[UserId])(*)).thenReturn(successful(apps))

  def fetchProductionSummariesByTeamMemberReturns(summaries: Seq[ApplicationSummary]) = 
    when(appsByTeamMemberServiceMock.fetchProductionSummariesByTeamMember(*[UserId])(*)).thenReturn(successful(summaries))

  def fetchSandboxSummariesByTeamMemberReturns(summaries: Seq[ApplicationSummary]) = 
    when(appsByTeamMemberServiceMock.fetchSandboxSummariesByTeamMember(*[UserId])(*)).thenReturn(successful(summaries))

  def fetchAllSummariesByTeamMemberReturns(sandbox: Seq[ApplicationSummary], production: Seq[ApplicationSummary]) =
    when(appsByTeamMemberServiceMock.fetchAllSummariesByTeamMember(*[UserId])(*)).thenReturn(successful((sandbox, production)))
}

object AppsByTeamMemberServiceMock extends AppsByTeamMemberServiceMock