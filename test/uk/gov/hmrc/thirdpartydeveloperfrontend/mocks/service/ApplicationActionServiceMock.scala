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

import scala.concurrent.Future
import scala.concurrent.Future.successful

import org.mockito.quality.Strictness
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}

import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ApiDefinition
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.{ApplicationWithCollaborators, ApplicationWithSubscriptionFields, ApplicationWithSubscriptions}
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.utils.FixedClock
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.UserSession
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.{ApplicationRequest, UserRequest}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.APISubscriptionStatus
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.ApplicationActionService

trait ApplicationActionServiceMock extends MockitoSugar with ArgumentMatchersSugar with FixedClock {

  val applicationActionServiceMock = mock[ApplicationActionService]

  def givenApplicationActionReturnsNotFound[A](applicationId: ApplicationId): Unit =
    when(applicationActionServiceMock.process[A](eqTo(applicationId), *)(*))
      .thenReturn(successful(None))

  def givenApplicationAction[A](application: ApplicationWithCollaborators, userSession: UserSession): Unit =
    givenApplicationAction[A](application.withSubscriptions(Set.empty).withFieldValues(Map.empty), userSession)

  def givenApplicationAction[A](application: ApplicationWithSubscriptions, userSession: UserSession): Unit =
    givenApplicationAction[A](application.withFieldValues(Map.empty), userSession)

  def givenApplicationAction[A](
      appData: ApplicationWithSubscriptionFields,
      userSession: UserSession,
      subscriptions: List[APISubscriptionStatus] = List.empty,
      openAccessApis: List[ApiDefinition] = List.empty
    ): Unit = {

    def createReturn(req: UserRequest[A]): Future[Option[ApplicationRequest[A]]] = {
      appData.roleFor(userSession.developer.email) match {
        case None       => successful(None)
        case Some(role) => successful(Some(
            new ApplicationRequest(
              application = appData.asAppWithCollaborators,
              deployedTo = appData.deployedTo,
              subscriptions,
              openAccessApis,
              role,
              userRequest = req
            )
          ))
      }
    }
    reset(applicationActionServiceMock)
    when(applicationActionServiceMock.process[A](eqTo(appData.id), *)(*))
      .thenAnswer((a: ApplicationId, request: UserRequest[A], c: HeaderCarrier) => createReturn(request))
  }

}

trait ApplicationActionServiceMockModule extends MockitoSugar with ArgumentMatchersSugar {

  protected trait BaseApplicationActionServiceMock {
    def aMock: ApplicationActionService
  }

  object ApplicationActionServiceMock extends BaseApplicationActionServiceMock {
    val aMock = mock[ApplicationActionService](withSettings.strictness(Strictness.LENIENT))
  }
}
