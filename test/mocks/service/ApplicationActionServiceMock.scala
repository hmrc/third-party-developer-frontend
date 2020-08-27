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

import org.mockito.MockitoSugar
import org.mockito.ArgumentMatchersSugar
import service.ApplicationActionService
import domain.models.applications.Application
import domain.models.applications.ApplicationWithSubscriptionData
import scala.concurrent.Future.{successful,failed}
import controllers.ApplicationRequest
import domain.models.developers.DeveloperSession
import play.api.mvc.MessagesRequest
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import uk.gov.hmrc.http.HeaderCarrier
import domain.models.applications.ApplicationId
import cats.data.OptionT
import cats.implicits._
import domain.models.apidefinitions.APISubscriptionStatus

trait ApplicationActionServiceMock extends MockitoSugar with ArgumentMatchersSugar {
  val applicationActionServiceMock = mock[ApplicationActionService]

  def givenApplicationActionReturnsNotFound[A](applicationId: ApplicationId): Unit =
    when(applicationActionServiceMock.process[A](eqTo(applicationId), *)(*,*))
    .thenReturn(OptionT.none[Future, ApplicationRequest[A]])

  def givenApplicationAction[A](application: Application,developerSession: DeveloperSession): Unit =
   givenApplicationAction[A](ApplicationWithSubscriptionData(application, Set.empty, Map.empty), developerSession)

  def givenApplicationAction[A](appData: ApplicationWithSubscriptionData, developerSession: DeveloperSession, subscriptions: Seq[APISubscriptionStatus] = Seq.empty): Unit = {

    def returns(req: MessagesRequest[A]): OptionT[Future,ApplicationRequest[A]] =
      appData.application.role(developerSession.developer.email) match {
        case None => OptionT.none[Future, ApplicationRequest[A]]
        case Some(role) => OptionT.pure[Future](
          ApplicationRequest(
            application = appData.application,
            deployedTo = appData.application.deployedTo,
            subscriptions,
            role,
            user = developerSession,
            request = req)
        )
      }

    when(applicationActionServiceMock.process[A](eqTo(appData.application.id), eqTo(developerSession))(*,*))
    .thenAnswer( (a:ApplicationId,b:DeveloperSession,request: MessagesRequest[A],d:HeaderCarrier) => returns(request))
  }

}
