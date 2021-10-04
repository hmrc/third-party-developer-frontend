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

package controllers.addapplication

import config.{ApplicationConfig, ErrorHandler, UpliftJourneyConfigProvider, On}
import connectors.ApmConnector
import controllers.{AddApplicationNameForm, ApplicationController, ChooseApplicationToUpliftForm}
import controllers.FormKeys.appNameField
import controllers.UserRequest
import domain.ApplicationCreatedResponse
import domain.Error._
import domain.models.apidefinitions.APISubscriptionStatus
import modules.uplift.services._
import modules.uplift.domain.models._
import domain.models.applications.Environment.{PRODUCTION, SANDBOX}
import domain.models.applications._
import domain.models.controllers.ApplicationSummary
import domain.models.emailpreferences.EmailPreferences
import play.api.data.Form
import play.api.libs.crypto.CookieSigner
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import service._
import uk.gov.hmrc.http.HeaderCarrier
import views.helper.EnvironmentNameService
import views.html._

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import config.OnDemand
import views.html.upliftJourney.BeforeYouStartView
import controllers.UserRequest

@Singleton
class AddApplication @Inject() (
    val errorHandler: ErrorHandler,
    val applicationService: ApplicationService,
    val applicationActionService: ApplicationActionService,
    val emailPreferencesService: EmailPreferencesService,
    val apmConnector: ApmConnector,
    val sessionService: SessionService,
    val auditService: AuditService,
    upliftLogic: UpliftLogic,
    mcc: MessagesControllerComponents,
    val cookieSigner: CookieSigner,
    accessTokenSwitchView: AccessTokenSwitchView,
    usingPrivilegedApplicationCredentialsView: UsingPrivilegedApplicationCredentialsView,
    tenDaysWarningView: TenDaysWarningView,
    addApplicationStartSubordinateView: AddApplicationStartSubordinateView,
    addApplicationStartPrincipalView: AddApplicationStartPrincipalView,
    addApplicationSubordinateSuccessView: AddApplicationSubordinateSuccessView,
    addApplicationNameView: AddApplicationNameView,
    chooseApplicationToUpliftView: ChooseApplicationToUpliftView,
    upliftJourneyConfigProvider: UpliftJourneyConfigProvider,
    beforeYouStartView: BeforeYouStartView,
    flowService: GetProductionCredentialsFlowService
)(implicit val ec: ExecutionContext, val appConfig: ApplicationConfig, val environmentNameService: EnvironmentNameService)
    extends ApplicationController(mcc) {

  def accessTokenSwitchPage(): Action[AnyContent] = loggedInAction { implicit request => 
    successful(Ok(accessTokenSwitchView())) 
  }

  def usingPrivilegedApplicationCredentialsPage(): Action[AnyContent] = loggedInAction { implicit request => 
    successful(Ok(usingPrivilegedApplicationCredentialsView())) 
  }
  
  def addApplicationSubordinate(): Action[AnyContent] = loggedInAction { implicit request => 
    successful(Ok(addApplicationStartSubordinateView())) 
  }
  
  def addApplicationPrincipal(): Action[AnyContent] = loggedInAction { implicit request => 
    
    def upliftJourneyTurnedOnInRequestHeader: Boolean = 
      request.headers.get("useNewUpliftJourney").fold(false) { setting =>
        Try(setting.toBoolean).getOrElse(false) 
      }

    upliftJourneyConfigProvider.status match { 
      case On => addApplicationProductionSwitch()(request)
      case OnDemand if upliftJourneyTurnedOnInRequestHeader => addApplicationProductionSwitch()(request)
      case _ => successful(Ok(addApplicationStartPrincipalView()))
    }
  }
  
  def tenDaysWarning(): Action[AnyContent] = loggedInAction { implicit request => 
    successful(Ok(tenDaysWarningView())) 
  }
  
  def addApplicationName(environment: Environment): Action[AnyContent] = loggedInAction { implicit request =>
    val form = AddApplicationNameForm.form.fill(AddApplicationNameForm(""))
    successful(Ok(addApplicationNameView(form, environment)))
  }

  def progressOnUpliftJourney(sandboxAppId: ApplicationId): Action[AnyContent] = loggedInAction { implicit request =>

    def upliftJourneyTurnedOnInRequestHeader: Boolean =
      request.headers.get("useNewUpliftJourney").fold(false) { setting =>
        Try(setting.toBoolean).getOrElse(false)
        }

    upliftJourneyConfigProvider.status match {
      case On => successful(Ok(beforeYouStartView(sandboxAppId)))
      case OnDemand if upliftJourneyTurnedOnInRequestHeader => successful(Ok(beforeYouStartView(sandboxAppId)))
      case _ => showConfirmSubscriptionsPage(sandboxAppId)(request)
    }
  }

  def soleApplicationToUpliftAction(appId: ApplicationId): Action[AnyContent] = loggedInAction { implicit request =>
    (for {
      (sandboxAppSummaries, upliftableAppIds) <- upliftLogic.aUsersSandboxAdminSummariesAndUpliftIds(loggedIn.developer.userId)
      upliftableSummaries = sandboxAppSummaries.filter(s => upliftableAppIds.contains(s.id))
    } yield upliftableSummaries match {
      case summary :: Nil => progressOnUpliftJourney(upliftableSummaries.head.id)(request)
      case _              => successful(BadRequest(Json.toJson(BadRequestError)))
    }).flatten
  }

  def addApplicationProductionSwitch(): Action[AnyContent] = loggedInAction { implicit request =>
    def chooseApplicationToUplift(upliftableSummaries: Seq[ApplicationSummary], showFluff: Boolean): Action[AnyContent] = loggedInAction { implicit request =>
      val form = 
        if(upliftableSummaries.size == 1) // TODO - and only one API sub
          ChooseApplicationToUpliftForm.form.fill(ChooseApplicationToUpliftForm(upliftableSummaries.head.id))
        else
          ChooseApplicationToUpliftForm.form

      successful(Ok(chooseApplicationToUpliftView(form, upliftableSummaries, showFluff)))
    }

    upliftLogic.aUsersSandboxAdminSummariesAndUpliftIds(loggedIn.developer.userId).flatMap { data =>
      val (summaries, upliftableAppIds) = data
      val upliftableSummaries = summaries.filter(s => upliftableAppIds.contains(s.id))
      val hasAppsThatCannotBeUplifted = upliftableSummaries.size < summaries.size

      upliftableAppIds.toList match {
        case Nil          => successful(BadRequest(Json.toJson(BadRequestError)))
        case appId :: Nil if !hasAppsThatCannotBeUplifted => progressOnUpliftJourney(appId)(request)
        case _ => chooseApplicationToUplift(upliftableSummaries, hasAppsThatCannotBeUplifted)(request)
      }
    }
  }
  
  private def showConfirmSubscriptionsPage(sandboxAppId: ApplicationId)(implicit request: UserRequest[_]) = {
    for {
      upliftableSubscriptions <- apmConnector.fetchUpliftableSubscriptions(sandboxAppId)
      apiSubscriptions         = ApiSubscriptions(upliftableSubscriptions.map(id => (id, true)).toMap)
      _ <- flowService.storeApiSubscriptions(apiSubscriptions, request.developerSession)
    }
    yield {
      Redirect( modules.uplift.controllers.routes.UpliftJourneyController.confirmApiSubscriptionsPage(sandboxAppId))
    }
  }

  def chooseApplicationToUpliftAction(): Action[AnyContent] = loggedInAction { implicit request =>

    def handleValidForm(validForm: ChooseApplicationToUpliftForm) =
      progressOnUpliftJourney(validForm.applicationId)(request)

    def handleInvalidForm(formWithErrors: Form[ChooseApplicationToUpliftForm]) = {
      upliftLogic.aUsersSandboxAdminSummariesAndUpliftIds(loggedIn.developer.userId) flatMap { data =>
      val (summaries, upliftableAppIds) = data
      val upliftableSummaries = summaries.filter(s => upliftableAppIds.contains(s.id))
      val haveAppsThatCannotBeUplifted = upliftableSummaries.size < summaries.size

      (upliftableSummaries.size, haveAppsThatCannotBeUplifted) match {
          case (0, _)     => successful(BadRequest(Json.toJson(BadRequestError)))
          case (1, false) => successful(BadRequest(Json.toJson(BadRequestError)))
          case _  => successful(BadRequest(chooseApplicationToUpliftView(formWithErrors, upliftableSummaries.toSeq, haveAppsThatCannotBeUplifted)))
        }
      }
    }      
    ChooseApplicationToUpliftForm.form.bindFromRequest().fold(handleInvalidForm, handleValidForm)
  }


  def editApplicationNameAction(environment: Environment): Action[AnyContent] = loggedInAction { implicit request =>
    val requestForm: Form[AddApplicationNameForm] = AddApplicationNameForm.form.bindFromRequest

    def nameApplicationWithErrors(errors: Form[AddApplicationNameForm], environment: Environment) =
      successful(Ok(addApplicationNameView(errors, environment)))

    def addApplication(form: AddApplicationNameForm): Future[ApplicationCreatedResponse] = {
      applicationService
        .createForUser(CreateApplicationRequest.fromAddApplicationJourney(loggedIn, form, environment))
    }

    def nameApplicationWithValidForm(formThatPassesSimpleValidation: AddApplicationNameForm) =
      applicationService
        .isApplicationNameValid(formThatPassesSimpleValidation.applicationName, environment, None)
        .flatMap {
          case Valid =>
            addApplication(formThatPassesSimpleValidation).map(applicationCreatedResponse =>
              environment match {
                case PRODUCTION => Redirect(controllers.checkpages.routes.ApplicationCheck.requestCheckPage(applicationCreatedResponse.id))
                case SANDBOX    => Redirect(controllers.routes.Subscriptions.addAppSubscriptions(applicationCreatedResponse.id))
              }
            )

          case invalid: Invalid =>
            def invalidApplicationNameForm = requestForm.withError(appNameField, invalid.validationErrorMessageKey)

            successful(BadRequest(addApplicationNameView(invalidApplicationNameForm, environment)))
        }

    requestForm.fold(formWithErrors => nameApplicationWithErrors(formWithErrors, environment), nameApplicationWithValidForm)
  }

  def addApplicationSuccess(applicationId: ApplicationId): Action[AnyContent] = {

    def subscriptionsNotInUserEmailPreferences(applicationSubscriptions: Seq[APISubscriptionStatus],
                                               userEmailPreferences: EmailPreferences)(implicit hc: HeaderCarrier): Future[Set[String]] = {
      emailPreferencesService.fetchAPIDetails(applicationSubscriptions.map(_.serviceName).toSet) map { apiDetails =>
        val allInCategories = userEmailPreferences.interests.filter(i => i.services.isEmpty).map(_.regime)
        val filteredApis = apiDetails.filter(api => api.categories.intersect(allInCategories).isEmpty)
        filteredApis.map(_.serviceName).diff(userEmailPreferences.interests.flatMap(_.services)).toSet
      }
    }

    whenTeamMemberOnApp(applicationId) { implicit appRequest =>
      import appRequest._

      deployedTo match {
        case SANDBOX    => {
          val alreadySelectedEmailPreferences: Boolean = request.flash.get("emailPreferencesSelected").contains("true")
          subscriptionsNotInUserEmailPreferences(subscriptions.filter(_.subscribed), user.developer.emailPreferences) map { missingSubscriptions =>

            if(alreadySelectedEmailPreferences || missingSubscriptions.isEmpty) {
              Ok(addApplicationSubordinateSuccessView(application.name, applicationId))
            } else {
              Redirect(controllers.profile.routes.EmailPreferences.selectApisFromSubscriptionsPage(applicationId))
                .flashing("missingSubscriptions" -> missingSubscriptions.mkString(","))
            }
          }
        }
        case PRODUCTION => successful(NotFound(errorHandler.notFoundTemplate(request)))
      }
    }
  }

}
