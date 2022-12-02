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

import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.ApplicationUpdateSuccessful
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.ApiIdentifier
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.{ApplicationId, CollaboratorActor}
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.SubscriptionsService

import scala.concurrent.Future.successful

trait SubscriptionsServiceMock extends MockitoSugar with ArgumentMatchersSugar {
  val subscriptionsServiceMock = mock[SubscriptionsService]

  def givenSubscribeToApiSucceeds(appId: ApplicationId, actor: CollaboratorActor, apiIdentifier: ApiIdentifier) =
    when(subscriptionsServiceMock.subscribeToApi(eqTo(appId), eqTo(actor), eqTo(apiIdentifier))(*)).thenReturn(successful(ApplicationUpdateSuccessful))

  def givenSubscribeToApiSucceeds() =
    when(subscriptionsServiceMock.subscribeToApi(*, *, *)(*)).thenReturn(successful(ApplicationUpdateSuccessful))

  def givenUnsubscribeFromApiSucceeds(appId: ApplicationId, actor: CollaboratorActor, apiIdentifier: ApiIdentifier) =
    when(subscriptionsServiceMock.unsubscribeFromApi(eqTo(appId), eqTo(actor), eqTo(apiIdentifier))(*)).thenReturn(successful(ApplicationUpdateSuccessful))

  def givenAppIsSubscribedToApi(appId: ApplicationId, apiIdentifier: ApiIdentifier) =
    when(subscriptionsServiceMock.isSubscribedToApi(eqTo(appId), eqTo(apiIdentifier))(*)).thenReturn(successful(true))

  def givenAppIsNotSubscribedToApi(appId: ApplicationId, apiIdentifier: ApiIdentifier) =
    when(subscriptionsServiceMock.isSubscribedToApi(eqTo(appId), eqTo(apiIdentifier))(*)).thenReturn(successful(false))
}

object SubscriptionsServiceMock extends SubscriptionsServiceMock
