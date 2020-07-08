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

package mocks.service

import domain._
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import service.ApplicationService
import uk.gov.hmrc.http.HeaderCarrier

import java.util.UUID

import scala.concurrent.Future.{failed, successful}

trait ApplicationServiceMock extends MockitoSugar {
  val applicationServiceMock = mock[ApplicationService]

  def fetchByApplicationIdReturns(id: String, returns: Application) : Unit =
    when(applicationServiceMock.fetchByApplicationId(eqTo(id))(any())).thenReturn(successful(Some(returns)))

  def fetchByApplicationIdReturns(application: Application) : Unit =
    fetchByApplicationIdReturns(application.id, application)

  def fetchByApplicationIdReturnsNone(id: String) =
    when(applicationServiceMock.fetchByApplicationId(eqTo(id))(any())).thenReturn(successful(None))

  def fetchByTeamMemberEmailReturns(apps: Seq[Application]) =
    when(applicationServiceMock.fetchByTeamMemberEmail(any())(any[HeaderCarrier]))
      .thenReturn(successful(apps))

  def fetchByTeamMemberEmailReturns(email: String, apps: Seq[Application]) =
    when(applicationServiceMock.fetchByTeamMemberEmail(eqTo(email))(any[HeaderCarrier]))
      .thenReturn(successful(apps))

  def fetchAllSubscriptionsReturns(subscriptions: Seq[APISubscription]) = {
    when(applicationServiceMock.fetchAllSubscriptions(any[Application])(any[HeaderCarrier]))
      .thenReturn(successful(subscriptions))
  }

  def givenApplicationHasSubs(application: Application, returns: Seq[APISubscriptionStatus]) =
    when(applicationServiceMock.apisWithSubscriptions(eqTo(application))(any())).thenReturn(successful(returns))

  def givenApplicationHasNoSubs(application: Application) =
    when(applicationServiceMock.apisWithSubscriptions(eqTo(application))(any())).thenReturn(successful(Seq.empty))

  def fetchCredentialsReturns(application: Application, tokens: ApplicationToken): Unit =
    when(applicationServiceMock.fetchCredentials(eqTo(application))(any())).thenReturn(successful(tokens))

  def givenSubscribeToApiSucceeds(app: Application, apiContext: String, apiVersion: String) =
    when(applicationServiceMock.subscribeToApi(eqTo(app), eqTo(apiContext), eqTo(apiVersion))(any())).thenReturn(successful(ApplicationUpdateSuccessful))

  def givenSubscribeToApiSucceeds() =
    when(applicationServiceMock.subscribeToApi(any(),any(),any())(any())).thenReturn(successful(ApplicationUpdateSuccessful))

  def ungivenSubscribeToApiSucceeds(app: Application, apiContext: String, apiVersion: String) =
    when(applicationServiceMock.unsubscribeFromApi(eqTo(app), eqTo(apiContext), eqTo(apiVersion))(any())).thenReturn(successful(ApplicationUpdateSuccessful))

  def givenAppIsSubscribedToApi(app: Application, apiName: String, apiContext: String, apiVersion: String) =
    when(applicationServiceMock.isSubscribedToApi(eqTo(app), eqTo(apiName), eqTo(apiContext), eqTo(apiVersion))(any())).thenReturn(successful(true))

  def givenAppIsNotSubscribedToApi(app: Application, apiName: String, apiContext: String, apiVersion: String) =
    when(applicationServiceMock.isSubscribedToApi(eqTo(app), eqTo(apiName), eqTo(apiContext), eqTo(apiVersion))(any())).thenReturn(successful(false))

  def givenApplicationNameIsValid() =
    when(applicationServiceMock.isApplicationNameValid(any(), any(), any())(any[HeaderCarrier])).thenReturn(successful(Valid))

  def givenApplicationNameIsInvalid(invalid: Invalid) =
    when(applicationServiceMock.isApplicationNameValid(any(), any(), any())(any[HeaderCarrier])).thenReturn(successful(invalid))

  def givenApplicationUpdateSucceeds() =
    when(applicationServiceMock.update(any[UpdateApplicationRequest])(any())).thenReturn(successful(ApplicationUpdateSuccessful))

  def givenRemoveTeamMemberSucceeds(loggedInUser: DeveloperSession) =
    when(applicationServiceMock.removeTeamMember(any(), any(), eqTo(loggedInUser.email))(any[HeaderCarrier]))
    .thenReturn(successful(ApplicationUpdateSuccessful))

  def givenUpdateCheckInformationSucceeds(app: Application) =
    when(applicationServiceMock.updateCheckInformation(eqTo(app), any())(any()))
    .thenReturn(successful(ApplicationUpdateSuccessful))

  def givenUpdateCheckInformationSucceeds(app: Application, checkInfo: CheckInformation) =
    when(applicationServiceMock.updateCheckInformation(eqTo(app), eqTo(checkInfo))(any()))
    .thenReturn(successful(ApplicationUpdateSuccessful))

  def givenAddClientSecretReturns(application: Application, email: String) = {
    val newSecretId = UUID.randomUUID().toString
    val newSecret = UUID.randomUUID().toString

    when(applicationServiceMock.addClientSecret(eqTo(application), eqTo(email))(any()))
      .thenReturn(successful((newSecretId, newSecret)))
  }

  def givenAddClientSecretFailsWith(application: Application, email: String, exception: Exception) = {
    when(applicationServiceMock.addClientSecret(eqTo(application), eqTo(email))(any()))
      .thenReturn(failed(exception))
  }

  def givenDeleteClientSecretSucceeds(application: Application, clientSecretId: String, email: String) = {
        when(
          applicationServiceMock
            .deleteClientSecret(eqTo(application), eqTo(clientSecretId), eqTo(email))(any[HeaderCarrier])
        )
        .thenReturn(successful(ApplicationUpdateSuccessful))
  }

  def updateApplicationSuccessful() = {
    when(applicationServiceMock.update(any[UpdateApplicationRequest])(any[HeaderCarrier]))
      .thenReturn(successful(ApplicationUpdateSuccessful))
  }

  def givenApplicationExists(application: Application) : Unit = {
    import utils.TestApplications.tokens

    fetchByApplicationIdReturns(application.id, application)

    when(applicationServiceMock.fetchCredentials(eqTo(application))(any())).thenReturn(successful(tokens()))

    givenApplicationHasNoSubs(application)
  }
}
