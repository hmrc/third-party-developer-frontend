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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers

import cats.data.NonEmptyList

import play.api.mvc._

import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ApiDefinition
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.Collaborator
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.tpd.core.domain.models.User
import uk.gov.hmrc.apiplatform.modules.tpd.session.domain.models.{LoggedInState, UserSession}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.{
  APISubscriptionStatus,
  APISubscriptionStatusWithSubscriptionFields,
  APISubscriptionStatusWithWritableSubscriptionField
}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._

class UserRequest[A](val userSession: UserSession, val msgRequest: MessagesRequest[A]) extends MessagesRequest[A](msgRequest, msgRequest.messagesApi) {
  lazy val sessionId = userSession.sessionId

  lazy val developer: User              = userSession.developer
  lazy val userId: UserId               = developer.userId
  lazy val email: LaxEmailAddress       = developer.email
  lazy val loggedInState: LoggedInState = userSession.loggedInState

  lazy val displayedName: String = developer.displayedName

  lazy val loggedInName: Option[String] =
    if (loggedInState.isLoggedIn) {
      Some(displayedName)
    } else {
      None
    }

}

class MaybeUserRequest[A](val userSession: Option[UserSession], request: MessagesRequest[A]) extends MessagesRequest[A](request, request.messagesApi)

trait HasApplication {
  def application: Application
}

class ApplicationRequest[A](
    val application: Application,
    val deployedTo: Environment,
    val subscriptions: List[APISubscriptionStatus],
    val openAccessApis: List[ApiDefinition],
    val role: Collaborator.Role,
    val userRequest: UserRequest[A]
  ) extends UserRequest[A](userRequest.userSession, userRequest.msgRequest) with HasApplication {

  def hasSubscriptionFields: Boolean = {
    subscriptions.exists(s => s.subscribed && s.fields.fields.nonEmpty)
  }
}

class ApplicationWithFieldDefinitionsRequest[A](
    val fieldDefinitions: NonEmptyList[APISubscriptionStatusWithSubscriptionFields],
    applicationRequest: ApplicationRequest[A]
  ) extends ApplicationRequest[A](
      applicationRequest.application,
      applicationRequest.deployedTo,
      applicationRequest.subscriptions,
      applicationRequest.openAccessApis,
      applicationRequest.role,
      applicationRequest.userRequest
    )

class ApplicationWithSubscriptionFieldPageRequest[A](
    val pageIndex: Int,
    val totalPages: Int,
    val apiSubscriptionStatus: APISubscriptionStatusWithSubscriptionFields,
    val apiDetails: uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.ManageSubscriptions.ApiDetails,
    applicationRequest: ApplicationRequest[A]
  ) extends ApplicationRequest[A](
      applicationRequest.application,
      applicationRequest.deployedTo,
      applicationRequest.subscriptions,
      applicationRequest.openAccessApis,
      applicationRequest.role,
      applicationRequest.userRequest
    )

class ApplicationWithSubscriptionFieldsRequest[A](
    val apiSubscription: APISubscriptionStatusWithSubscriptionFields,
    applicationRequest: ApplicationRequest[A]
  ) extends ApplicationRequest[A](
      applicationRequest.application,
      applicationRequest.deployedTo,
      applicationRequest.subscriptions,
      applicationRequest.openAccessApis,
      applicationRequest.role,
      applicationRequest.userRequest
    )

class ApplicationWithWritableSubscriptionField[A](
    val subscriptionWithSubscriptionField: APISubscriptionStatusWithWritableSubscriptionField,
    applicationRequest: ApplicationRequest[A]
  ) extends ApplicationRequest[A](
      applicationRequest.application,
      applicationRequest.deployedTo,
      applicationRequest.subscriptions,
      applicationRequest.openAccessApis,
      applicationRequest.role,
      applicationRequest.userRequest
    )
