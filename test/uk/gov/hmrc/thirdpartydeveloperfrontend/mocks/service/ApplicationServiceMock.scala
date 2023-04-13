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

import java.util.UUID
import scala.concurrent.Future.{failed, successful}

import org.mockito.{ArgumentMatchersSugar, MockitoSugar}

import uk.gov.hmrc.apiplatform.modules.applications.domain.models.ApplicationId
import uk.gov.hmrc.apiplatform.modules.common.domain.models.Actors
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.ApplicationService
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.{CollaboratorTracker, LocalUserIdTracker, TestApplications}

trait ApplicationServiceMock extends MockitoSugar with ArgumentMatchersSugar with TestApplications with CollaboratorTracker with LocalUserIdTracker {
  val applicationServiceMock = mock[ApplicationService]

  def fetchByApplicationIdReturns(id: ApplicationId, returns: Application): Unit =
    fetchByApplicationIdReturns(id, ApplicationWithSubscriptionData(returns, Set.empty, Map.empty))

  def fetchByApplicationIdReturns(returns: Application): Unit =
    fetchByApplicationIdReturns(returns.id, ApplicationWithSubscriptionData(returns, Set.empty, Map.empty))

  def fetchByApplicationIdReturns(id: ApplicationId, returns: ApplicationWithSubscriptionData): Unit =
    when(applicationServiceMock.fetchByApplicationId(eqTo(id))(*)).thenReturn(successful(Some(returns)))

  def fetchByApplicationIdReturns(appData: ApplicationWithSubscriptionData): Unit =
    fetchByApplicationIdReturns(appData.application.id, appData)

  def fetchByApplicationIdReturnsNone(id: ApplicationId) =
    when(applicationServiceMock.fetchByApplicationId(eqTo(id))(*)).thenReturn(successful(None))

  def fetchCredentialsReturns(application: Application, tokens: ApplicationToken): Unit =
    when(applicationServiceMock.fetchCredentials(eqTo(application))(*)).thenReturn(successful(tokens))

  def givenApplicationNameIsValid() =
    when(applicationServiceMock.isApplicationNameValid(*, *, *[Option[ApplicationId]])(*)).thenReturn(successful(Valid))

  def givenApplicationNameIsInvalid(invalid: Invalid) =
    when(applicationServiceMock.isApplicationNameValid(*, *, *[Option[ApplicationId]])(*)).thenReturn(successful(invalid))

  def givenApplicationUpdateSucceeds() =
    when(applicationServiceMock.update(any[UpdateApplicationRequest])(*)).thenReturn(successful(ApplicationUpdateSuccessful))

  def givenUpdateCheckInformationSucceeds(app: Application) =
    when(applicationServiceMock.updateCheckInformation(eqTo(app), *)(*))
      .thenReturn(successful(ApplicationUpdateSuccessful))

  def givenUpdateCheckInformationSucceeds(app: Application, checkInfo: CheckInformation) =
    when(applicationServiceMock.updateCheckInformation(eqTo(app), eqTo(checkInfo))(*))
      .thenReturn(successful(ApplicationUpdateSuccessful))

  def givenAddClientSecretReturns(application: Application, actor: Actors.AppCollaborator) = {
    val newSecretId = UUID.randomUUID().toString
    val newSecret   = UUID.randomUUID().toString

    when(applicationServiceMock.addClientSecret(eqTo(application), eqTo(actor))(*))
      .thenReturn(successful((newSecretId, newSecret)))
  }

  def givenAddClientSecretFailsWith(application: Application, actor: Actors.AppCollaborator, exception: Exception) = {
    when(applicationServiceMock.addClientSecret(eqTo(application), eqTo(actor))(*))
      .thenReturn(failed(exception))
  }

  def givenDeleteClientSecretSucceeds(application: Application, actor: Actors.AppCollaborator, clientSecretId: String) = {
    when(
      applicationServiceMock
        .deleteClientSecret(eqTo(application), eqTo(actor), eqTo(clientSecretId))(*)
    ).thenReturn(successful(ApplicationUpdateSuccessful))
  }

  def updateApplicationSuccessful() = {
    when(applicationServiceMock.update(any[UpdateApplicationRequest])(*))
      .thenReturn(successful(ApplicationUpdateSuccessful))
  }

  def givenApplicationExists(application: Application): Unit = givenApplicationExists(ApplicationWithSubscriptionData(application, Set.empty, Map.empty))

  def givenApplicationExists(appData: ApplicationWithSubscriptionData): Unit = {
    fetchByApplicationIdReturns(appData.application.id, appData)

    when(applicationServiceMock.fetchCredentials(eqTo(appData.application))(*)).thenReturn(successful(tokens()))
  }

  def acceptResponsibleIndividualVerification(appId: ApplicationId, code: String) = {
    when(applicationServiceMock.acceptResponsibleIndividualVerification(eqTo(appId), eqTo(code))(*)).thenReturn(successful(ApplicationUpdateSuccessful))
  }

  def declineResponsibleIndividualVerification(appId: ApplicationId, code: String) = {
    when(applicationServiceMock.declineResponsibleIndividualVerification(eqTo(appId), eqTo(code))(*)).thenReturn(successful(ApplicationUpdateSuccessful))
  }
}

object ApplicationServiceMock extends ApplicationServiceMock
