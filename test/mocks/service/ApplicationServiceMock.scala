/*
 * Copyright 2022 HM Revenue & Customs
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

import java.util.UUID

import domain._
import domain.models.applications._
import domain.models.developers.DeveloperSession
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.ApplicationService

import scala.concurrent.Future.{failed, successful}
import domain.models.apidefinitions.ApiIdentifier
import utils.TestApplications
import utils.CollaboratorTracker
import utils.LocalUserIdTracker

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

  def givenSubscribeToApiSucceeds(app: Application, apiIdentifier: ApiIdentifier) =
    when(applicationServiceMock.subscribeToApi(eqTo(app), eqTo(apiIdentifier))(*)).thenReturn(successful(ApplicationUpdateSuccessful))

  def givenSubscribeToApiSucceeds() =
    when(applicationServiceMock.subscribeToApi(*, *)(*)).thenReturn(successful(ApplicationUpdateSuccessful))

  def ungivenSubscribeToApiSucceeds(app: Application, apiIdentifier: ApiIdentifier) =
    when(applicationServiceMock.unsubscribeFromApi(eqTo(app), eqTo(apiIdentifier))(*)).thenReturn(successful(ApplicationUpdateSuccessful))

  def givenAppIsSubscribedToApi(appId: ApplicationId, apiIdentifier: ApiIdentifier) =
    when(applicationServiceMock.isSubscribedToApi(eqTo(appId), eqTo(apiIdentifier))(*)).thenReturn(successful(true))

  def givenAppIsNotSubscribedToApi(appId: ApplicationId, apiIdentifier: ApiIdentifier) =
    when(applicationServiceMock.isSubscribedToApi(eqTo(appId), eqTo(apiIdentifier))(*)).thenReturn(successful(false))

  def givenApplicationNameIsValid() =
    when(applicationServiceMock.isApplicationNameValid(*, *, *[Option[ApplicationId]])(*)).thenReturn(successful(Valid))

  def givenApplicationNameIsInvalid(invalid: Invalid) =
    when(applicationServiceMock.isApplicationNameValid(*, *, *[Option[ApplicationId]])(*)).thenReturn(successful(invalid))

  def givenApplicationUpdateSucceeds() =
    when(applicationServiceMock.update(any[UpdateApplicationRequest])(*)).thenReturn(successful(ApplicationUpdateSuccessful))

  def givenRemoveTeamMemberSucceeds(loggedInDeveloper: DeveloperSession) =
    when(applicationServiceMock.removeTeamMember(*, *, eqTo(loggedInDeveloper.email))(*))
      .thenReturn(successful(ApplicationUpdateSuccessful))

  def givenUpdateCheckInformationSucceeds(app: Application) =
    when(applicationServiceMock.updateCheckInformation(eqTo(app), *)(*))
      .thenReturn(successful(ApplicationUpdateSuccessful))

  def givenUpdateCheckInformationSucceeds(app: Application, checkInfo: CheckInformation) =
    when(applicationServiceMock.updateCheckInformation(eqTo(app), eqTo(checkInfo))(*))
      .thenReturn(successful(ApplicationUpdateSuccessful))

  def givenAddClientSecretReturns(application: Application, email: String) = {
    val newSecretId = UUID.randomUUID().toString
    val newSecret = UUID.randomUUID().toString

    when(applicationServiceMock.addClientSecret(eqTo(application), eqTo(email))(*))
      .thenReturn(successful((newSecretId, newSecret)))
  }

  def givenAddClientSecretFailsWith(application: Application, email: String, exception: Exception) = {
    when(applicationServiceMock.addClientSecret(eqTo(application), eqTo(email))(*))
      .thenReturn(failed(exception))
  }

  def givenDeleteClientSecretSucceeds(application: Application, clientSecretId: String, email: String) = {
    when(
      applicationServiceMock
        .deleteClientSecret(eqTo(application), eqTo(clientSecretId), eqTo(email))(*)
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
}

object ApplicationServiceMock extends ApplicationServiceMock