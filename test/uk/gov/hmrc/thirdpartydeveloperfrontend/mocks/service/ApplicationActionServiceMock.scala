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

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import cats.data.OptionT
import cats.implicits._
import org.mockito.quality.Strictness
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}

import uk.gov.hmrc.http.HeaderCarrier

import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ApiDefinition
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.UserSession
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.{ApplicationRequest, UserRequest}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.APISubscriptionStatus
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.{Application, ApplicationWithSubscriptionData}
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.ApplicationActionService

trait ApplicationActionServiceMock extends MockitoSugar with ArgumentMatchersSugar {
  val applicationActionServiceMock = mock[ApplicationActionService]

  def givenApplicationActionReturnsNotFound[A](applicationId: ApplicationId): Unit =
    when(applicationActionServiceMock.process[A](eqTo(applicationId), *)(*))
      .thenReturn(OptionT.none[Future, ApplicationRequest[A]])

  def givenApplicationAction[A](application: Application, userSession: UserSession): Unit =
    givenApplicationAction[A](ApplicationWithSubscriptionData(application, Set.empty, Map.empty), userSession)

  def givenApplicationAction[A](
      appData: ApplicationWithSubscriptionData,
      userSession: UserSession,
      subscriptions: List[APISubscriptionStatus] = List.empty,
      openAccessApis: List[ApiDefinition] = List.empty
    ): Unit = {

    def createReturn(req: UserRequest[A]): OptionT[Future, ApplicationRequest[A]] = {
      appData.application.role(userSession.developer.email) match {
        case None       => OptionT.none[Future, ApplicationRequest[A]]
        case Some(role) => OptionT.pure[Future](
            new ApplicationRequest(
              application = appData.application,
              deployedTo = appData.application.deployedTo,
              subscriptions,
              openAccessApis,
              role,
              userRequest = req
            )
          )
      }
    }
    when(applicationActionServiceMock.process[A](eqTo(appData.application.id), *)(*))
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
