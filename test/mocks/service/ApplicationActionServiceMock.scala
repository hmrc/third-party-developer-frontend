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

import cats.data.OptionT
import cats.implicits._
import controllers.ApplicationRequest
import domain.models.apidefinitions.APISubscriptionStatus
import domain.models.applications.{Application, ApplicationId, ApplicationWithSubscriptionData}
import domain.models.developers.DeveloperSession
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import play.api.mvc.MessagesRequest
import service.ApplicationActionService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import domain.models.apidefinitions.ApiContext
import domain.models.subscriptions.ApiData
import controllers.UserRequest

trait ApplicationActionServiceMock extends MockitoSugar with ArgumentMatchersSugar {
  val applicationActionServiceMock = mock[ApplicationActionService]

  def givenApplicationActionReturnsNotFound[A](applicationId: ApplicationId): Unit =
    when(applicationActionServiceMock.process[A](eqTo(applicationId), *)(*,*))
    .thenReturn(OptionT.none[Future, ApplicationRequest[A]])

  def givenApplicationAction[A](application: Application, developerSession: DeveloperSession): Unit =
   givenApplicationAction[A](ApplicationWithSubscriptionData(application, Set.empty, Map.empty), developerSession)

  def givenApplicationAction[A](appData: ApplicationWithSubscriptionData, developerSession: DeveloperSession, subscriptions: List[APISubscriptionStatus] = List.empty, openAccessApis: Map[ApiContext, ApiData] = Map.empty): Unit = {

    def returns(req: MessagesRequest[A]): OptionT[Future,ApplicationRequest[A]] =
      appData.application.role(developerSession.developer.email) match {
        case None => OptionT.none[Future, ApplicationRequest[A]]
        case Some(role) => OptionT.pure[Future](
          new ApplicationRequest(
            application = appData.application,
            deployedTo = appData.application.deployedTo,
            subscriptions,
            openAccessApis,
            role,
            userRequest = new UserRequest(developerSession,req)
          )
        )
      }

    when(applicationActionServiceMock.process[A](eqTo(appData.application.id), eqTo(developerSession))(*,*))
    .thenAnswer( (a:ApplicationId, b:DeveloperSession, request: MessagesRequest[A], d:HeaderCarrier) => returns(request))
  }

}
