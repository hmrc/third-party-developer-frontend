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

import scala.concurrent.Future.successful

import org.mockito.{ArgumentMatchersSugar, MockitoSugar}

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models._
import uk.gov.hmrc.apiplatform.modules.applications.core.interface.models._
import uk.gov.hmrc.apiplatform.modules.common.domain.models.ApplicationId
import uk.gov.hmrc.apiplatform.modules.common.services.ClockNow
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.ApplicationService
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.TokenProvider

trait ApplicationServiceMock extends MockitoSugar with ArgumentMatchersSugar with TokenProvider {
  self: ClockNow =>

  val applicationServiceMock = mock[ApplicationService]

  def fetchByApplicationIdReturns(id: ApplicationId, returns: ApplicationWithCollaborators): Unit =
    fetchByApplicationIdReturns(id, returns.withSubscriptions(Set.empty).withFieldValues(Map.empty))

  def fetchByApplicationIdReturns(returns: ApplicationWithCollaborators): Unit =
    fetchByApplicationIdReturns(returns.id, returns.withSubscriptions(Set.empty).withFieldValues(Map.empty))

  def fetchByApplicationIdReturns(id: ApplicationId, returns: ApplicationWithSubscriptionFields): Unit =
    when(applicationServiceMock.fetchByApplicationId(eqTo(id))(*)).thenReturn(successful(Some(returns)))

  def fetchByApplicationIdReturns(appData: ApplicationWithSubscriptions): Unit =
    fetchByApplicationIdReturns(appData.id, appData.withFieldValues(Map.empty))

  def fetchByApplicationIdReturnsNone(id: ApplicationId) =
    when(applicationServiceMock.fetchByApplicationId(eqTo(id))(*)).thenReturn(successful(None))

  def fetchCredentialsReturns(application: ApplicationWithCollaborators, tokens: ApplicationToken): Unit =
    when(applicationServiceMock.fetchCredentials(eqTo(application))(*)).thenReturn(successful(tokens))

  def givenApplicationNameIsValid() =
    when(applicationServiceMock.isApplicationNameValid(*, *, *[Option[ApplicationId]])(*)).thenReturn(successful(ApplicationNameValidationResult.Valid))

  def givenApplicationNameIsInvalid() =
    when(applicationServiceMock.isApplicationNameValid(*, *, *[Option[ApplicationId]])(*)).thenReturn(successful(ApplicationNameValidationResult.Invalid))

  def givenApplicationNameIsDuplicate() =
    when(applicationServiceMock.isApplicationNameValid(*, *, *[Option[ApplicationId]])(*)).thenReturn(successful(ApplicationNameValidationResult.Duplicate))

  def givenApplicationExists(application: ApplicationWithCollaborators): Unit = givenApplicationExists(application.withSubscriptions(Set.empty))

  def givenApplicationExists(application: ApplicationWithSubscriptions): Unit = givenApplicationExists(application.withFieldValues(Map.empty))

  def givenApplicationExists(application: ApplicationWithSubscriptionFields): Unit = {

    fetchByApplicationIdReturns(application.id, application)

    when(applicationServiceMock.fetchCredentials(eqTo(application.asAppWithCollaborators))(*)).thenReturn(successful(tokens()))
  }

  def acceptResponsibleIndividualVerification(appId: ApplicationId, code: String) = {
    when(applicationServiceMock.acceptResponsibleIndividualVerification(eqTo(appId), eqTo(code))(*)).thenReturn(successful(ApplicationUpdateSuccessful))
  }

  def declineResponsibleIndividualVerification(appId: ApplicationId, code: String) = {
    when(applicationServiceMock.declineResponsibleIndividualVerification(eqTo(appId), eqTo(code))(*)).thenReturn(successful(ApplicationUpdateSuccessful))
  }
}

object ApplicationServiceMock extends ApplicationServiceMock with TokenProvider with FixedClock
