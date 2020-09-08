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

package controllers

import cats.data.NonEmptyList
import config.{ApplicationConfig, ErrorHandler}
import domain.models.applications.ApplicationId
import domain.models.applications.Capabilities.ViewPushSecret
import domain.models.applications.Permissions.SandboxOrAdmin
import javax.inject.{Inject, Singleton}
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import service.{ApplicationActionService, ApplicationService, PushPullNotificationsService, SessionService}
import views.html.ppns.PushSecretsView

import scala.concurrent.ExecutionContext

@Singleton
class PushPullNotifications @Inject() (
                                        override val sessionService: SessionService,
                                        override val applicationService: ApplicationService,
                                        override val errorHandler: ErrorHandler,
                                        override val cookieSigner: CookieSigner,
                                        override val applicationActionService: ApplicationActionService,
                                        mcc: MessagesControllerComponents,
                                        pushSecretsView: PushSecretsView,
                                        pushPullNotificationsService: PushPullNotificationsService
                                      )(implicit override val ec: ExecutionContext, override val appConfig: ApplicationConfig)
  extends ApplicationController(mcc) {

  def showPushSecrets(applicationId: ApplicationId): Action[AnyContent] = subscribedToApiWithPpnsFieldAction(applicationId, ViewPushSecret, SandboxOrAdmin) {
    implicit request: ApplicationRequest[AnyContent] =>
      pushPullNotificationsService.fetchPushSecrets(request.application) map { pushSecrets =>
        NonEmptyList.fromList(pushSecrets.toList)
          .fold(NotFound(errorHandler.notFoundTemplate))(nonEmptySecrets => Ok(pushSecretsView(request.application, nonEmptySecrets)))
    }
  }
}
