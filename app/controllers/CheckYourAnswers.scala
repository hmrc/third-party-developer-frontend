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
import config.{ApplicationConfig, ErrorHandler}
import domain.Capabilities.SupportsAppChecks
import domain.Permissions.AdministratorOnly
import domain.{Application, CheckInformation}
import javax.inject.{Inject, Singleton}
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, AnyContent, Result}
import service.{ApplicationService, SessionService, SubscriptionsService}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CheckYourAnswers @Inject()(val applicationService: ApplicationService,
                                 val apiSubscriptionsHelper: ApiSubscriptionsHelper,
                                 val sessionService: SessionService,
                                 val errorHandler: ErrorHandler,
                                 val messagesApi: MessagesApi
                                )
                                (implicit val ec: ExecutionContext, val appConfig: ApplicationConfig)
  extends ApplicationController() with ApplicationHelper {

  private def canUseChecksAction(applicationId: String)
    (fun: ApplicationRequest[AnyContent] => Future[Result]): Action[AnyContent] =
    capabilityThenPermissionsAction(SupportsAppChecks,AdministratorOnly)(applicationId)(fun)

  def answersPage(appId: String): Action[AnyContent] = whenTeamMemberOnApp(appId){ implicit request =>

    for {
      application <- fetchApp(appId)
      softwareName = application.name
      privacyPolicyUrl = application.privacyPolicyUrl
      termsAndConditionsUrl = application.termsAndConditionsUrl
      acceptedToTermsOfUse = application.checkInformation.fold(false)(_.termsOfUseAgreements.nonEmpty)

      contactDetails = application.checkInformation.flatMap(_.contactDetails)
      fullName = contactDetails.map(_.fullname)
      email = contactDetails.map(_.email)
      telephoneNumber = contactDetails.map(_.telephoneNumber)

      subscriptions <- applicationService.fetchAllSubscriptions(application).map(_.map(_.name))

    } yield Ok(views.html.checkYourAnswers(appId, softwareName, fullName, email, telephoneNumber, privacyPolicyUrl, termsAndConditionsUrl, acceptedToTermsOfUse, subscriptions))
  }
}
