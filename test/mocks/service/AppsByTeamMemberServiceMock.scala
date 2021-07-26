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

package mocks.service

import domain.models.applications._
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}

import scala.concurrent.Future.successful
import domain.models.developers.UserId
import domain.models.controllers.ApplicationSummary
import service.AppsByTeamMemberService

trait AppsByTeamMemberServiceMock extends MockitoSugar with ArgumentMatchersSugar {
  val appsByTeamMemberServiceMock = mock[AppsByTeamMemberService]

  def fetchAppsByTeamMemberReturns(environment: Environment)(apps: Seq[Application]) = 
    when(appsByTeamMemberServiceMock.fetchAppsByTeamMember(eqTo(environment))(*[UserId])(*))
    .thenReturn(successful(apps.map(_.copy(deployedTo = environment))))
    
  def fetchByTeamMembersWithRoleReturns(apps: Seq[Application]) = 
    when(appsByTeamMemberServiceMock.fetchByTeamMemberWithRole(*)(*)(*[UserId])(*)).thenReturn(successful(apps))

  def fetchProductionSummariesByTeamMemberReturns(summaries: Seq[ApplicationSummary]) = 
    when(appsByTeamMemberServiceMock.fetchProductionSummariesByTeamMember(*[UserId])(*)).thenReturn(successful(summaries))

  def fetchSandboxSummariesByTeamMemberReturns(summaries: Seq[ApplicationSummary]) = 
    when(appsByTeamMemberServiceMock.fetchSandboxSummariesByTeamMember(*[UserId])(*)).thenReturn(successful(summaries))

  def fetchSandboxAppsByTeamMemberReturns(apps: Seq[Application]) = ???

  def fetchSandboxSummariesByAdminReturns = ???

  def fetchAllSummariesByTeamMemberReturns(sandbox: Seq[ApplicationSummary], production: Seq[ApplicationSummary]) =
    when(appsByTeamMemberServiceMock.fetchAllSummariesByTeamMember(*[UserId])(*)).thenReturn(successful((sandbox, production)))
}
object AppsByTeamMemberServiceMock extends AppsByTeamMemberServiceMock