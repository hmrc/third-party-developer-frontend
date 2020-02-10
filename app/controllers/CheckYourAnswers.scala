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
import domain.{Application, ApplicationAlreadyExists, CheckInformation, CheckInformationForm, DeskproTicketCreationFailed}
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, AnyContent, Result}
import service.{ApplicationService, SessionService, SubscriptionsService}
import views.html.applicationcheck

import scala.concurrent.{ExecutionContext, Future}

case class CheckAnswersData(
  appId: String,
  softwareName: String,
  fullName: Option[String],
  email: Option[String],
  telephoneNumber: Option[String],
  privacyPolicyUrl: Option[String],
  termsAndConditionsUrl: Option[String],
  acceptedTermsOfUse: Boolean,
  subscriptions: Seq[String]
)

@Singleton
class CheckYourAnswers @Inject()(val applicationService: ApplicationService,
                                 val apiSubscriptionsHelper: ApiSubscriptionsHelper,
                                 val applicationCheck: ApplicationCheck,
                                 val sessionService: SessionService,
                                 val errorHandler: ErrorHandler,
                                 val messagesApi: MessagesApi
                                )
                                (implicit val ec: ExecutionContext, val appConfig: ApplicationConfig)
  extends ApplicationController() with ApplicationHelper {

  private def canUseChecksAction(applicationId: String)
    (fun: ApplicationRequest[AnyContent] => Future[Result]): Action[AnyContent] =
    capabilityThenPermissionsAction(SupportsAppChecks,AdministratorOnly)(applicationId)(fun)

  private def populateCheckYourAnswersData(app: Application): CheckAnswersData = {

  }
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
      formData = CheckAnswersData(appId, softwareName, fullName, email, telephoneNumber, privacyPolicyUrl, termsAndConditionsUrl, acceptedToTermsOfUse, subscriptions)
    } yield Ok(views.html.checkYourAnswers(formData))
  }

  def answersPageAction(appId: String) = canUseChecksAction(appId) { implicit request =>
//    def withValidForm(app: Application, requestForm: Form[String])(form: CheckInformationForm): Future[Result] = {
    //        case _: ApplicationAlreadyExists =>
    //          val information = app.checkInformation.getOrElse(CheckInformation()).copy(confirmedName = false)
    //          applicationService.updateCheckInformation(app.id, information)
    //          Conflict(applicationcheck.landingPage(app.copy(checkInformation = Some(information)), requestForm.withError("confirmedName", applicationNameAlreadyExistsKey)))
    //      }
    //    }
    val app = request.application
    val future = for {
      _ <- applicationService.requestUplift(appId, app.name, request.user)
    } yield Redirect(routes.ApplicationCheck.credentialsRequested(appId))

    future recover {
      case e: DeskproTicketCreationFailed => InternalServerError(views.html.checkYourAnswers())//(app, requestForm.withError("submitError", e.displayMessage)))
    }
  }
}
