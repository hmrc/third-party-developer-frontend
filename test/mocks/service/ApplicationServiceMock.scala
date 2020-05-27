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
import org.scalatest.mockito.MockitoSugar
import service.ApplicationService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future.successful

trait ApplicationServiceMock extends MockitoSugar {
  val applicationServiceMock = mock[ApplicationService]

  def fetchByApplicationIdReturns(id: String, returns: Application) =
    when(applicationServiceMock.fetchByApplicationId(eqTo(id))(any())).thenReturn(successful(Some(returns)))

  def fetchByTeamMemberEmailReturns(apps: Seq[Application]) =
    when(applicationServiceMock.fetchByTeamMemberEmail(any())(any[HeaderCarrier]))
      .thenReturn(successful(apps))

  def fetchByTeamMemberEmailReturns(email: String, apps: Seq[Application]) =
    when(applicationServiceMock.fetchByTeamMemberEmail(eqTo(email))(any[HeaderCarrier]))
      .thenReturn(successful(apps))

  def givenApplicationHasSubs(application: Application, returns: Seq[APISubscriptionStatus]) =
    when(applicationServiceMock.apisWithSubscriptions(eqTo(application))(any())).thenReturn(successful(returns))

  def givenApplicationHasNoSubs(application: Application) =
    when(applicationServiceMock.apisWithSubscriptions(eqTo(application))(any())).thenReturn(successful(Seq.empty))

  def fetchCredentialsReturns(id: String, tokens: ApplicationToken): Unit =
    when(applicationServiceMock.fetchCredentials(eqTo(id))(any())).thenReturn(successful(tokens))

  def fetchCredentialsReturns(application: Application, tokens: ApplicationToken): Unit =
    fetchCredentialsReturns(application.id, tokens)

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

  def givenUpdateCheckInformationReturns(appId: String) =
    when(applicationServiceMock.updateCheckInformation(eqTo(appId), any())(any()))
    .thenReturn(successful(ApplicationUpdateSuccessful))

  def givenApplicationExists(application: Application) : Unit = {
    import utils.TestApplications.tokens

    fetchByApplicationIdReturns(application.id, application)

    when(applicationServiceMock.fetchCredentials(eqTo(application.id))(any())).thenReturn(successful(tokens()))

    givenApplicationHasNoSubs(application)
  }
}
