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
import controllers.FormKeys.applicationNameAlreadyExistsKey
import domain.Capabilities.SupportsAppChecks
import domain.Permissions.AdministratorOnly
import domain.{Application, ApplicationAlreadyExists, CheckInformation, CheckInformationForm, ContactDetails, DeskproTicketCreationFailed}
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, AnyContent, Result}
import service.{ApplicationService, SessionService}
import views.html.applicationcheck

import scala.concurrent.{ExecutionContext, Future}

case class CheckYourAnswersData(
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

case class DummyCheckYourAnswersForm(dummy: String = "dummy")

object CheckYourAnswersForm {
  import play.api.data.Forms.{ignored, mapping}

  def form: Form[DummyCheckYourAnswersForm] = {
    Form(mapping(
      "dummy" -> ignored("dummy")
      )
      (DummyCheckYourAnswersForm.apply)
      (DummyCheckYourAnswersForm.unapply)
    )
  }
}


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
    capabilityThenPermissionsAction(SupportsAppChecks, AdministratorOnly)(applicationId)(fun)

  private def populateCheckYourAnswersData(application: Application, subs: Seq[String]): CheckYourAnswersData = {
    val contactDetails: Option[ContactDetails] = application.checkInformation.flatMap(_.contactDetails)

    println(subs)

    CheckYourAnswersData(
      appId = application.id,
      softwareName = application.name,

      fullName = contactDetails.map(_.fullname),
      email = contactDetails.map(_.email),
      telephoneNumber = contactDetails.map(_.telephoneNumber),

      privacyPolicyUrl = application.privacyPolicyUrl,
      termsAndConditionsUrl = application.termsAndConditionsUrl,
      acceptedTermsOfUse = application.checkInformation.fold(false)(_.termsOfUseAgreements.nonEmpty),
      subscriptions = subs
    )
  }

  private def populateCheckYourAnswersData(application: Application)(implicit request: ApplicationRequest[AnyContent]): Future[CheckYourAnswersData] = {
    applicationService.fetchAllSubscriptions(application).map(_.map(_.name)).map(subscriptions => {
      populateCheckYourAnswersData(application, subscriptions)
    })
  }

  def answersPage(appId: String): Action[AnyContent] = canUseChecksAction(appId) { implicit request =>
    for {
      application <- fetchApp(appId)
      checkYourAnswersData <- populateCheckYourAnswersData(application)
    } yield Ok(views.html.checkYourAnswers(checkYourAnswersData, CheckYourAnswersForm.form.fillAndValidate(DummyCheckYourAnswersForm("dummy"))))
  }

  def answersPageAction(appId: String) = canUseChecksAction(appId) { implicit request =>
    val application = request.application

    (for {
      _ <- applicationService.requestUplift(appId, application.name, request.user)
    } yield Redirect(routes.ApplicationCheck.credentialsRequested(appId)))
    .recoverWith {
      case e: DeskproTicketCreationFailed =>
        for {
          checkYourAnswersData <- populateCheckYourAnswersData(application)
          requestForm = CheckYourAnswersForm.form.fillAndValidate(DummyCheckYourAnswersForm("dummy"))
        } yield InternalServerError(views.html.checkYourAnswers(checkYourAnswersData, requestForm.withError("submitError", e.displayMessage)))
    }
    .recover {
      case _: ApplicationAlreadyExists =>
        val information = application.checkInformation.getOrElse(CheckInformation()).copy(confirmedName = false)
        applicationService.updateCheckInformation(application.id, information)
        val requestForm = ApplicationInformationForm.form.fillAndValidate(CheckInformationForm.fromCheckInformation(application.checkInformation.getOrElse(CheckInformation())))
        Conflict(applicationcheck.landingPage(application.copy(checkInformation = Some(information)), requestForm.withError("confirmedName", applicationNameAlreadyExistsKey)))
    }
  }
}
