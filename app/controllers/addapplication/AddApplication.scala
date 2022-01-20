/*
 * Copyright 2022 HM Revenue & Customs
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

import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ErrorHandler
import connectors.ApmConnector
import controllers.{AddApplicationNameForm, ApplicationController, ChooseApplicationToUpliftForm}
import controllers.FormKeys.appNameField
import controllers.UserRequest
import domain.ApplicationCreatedResponse
import domain.Error._
import domain.models.apidefinitions.APISubscriptionStatus
import uk.gov.hmrc.modules.uplift.services._
import uk.gov.hmrc.modules.uplift.domain.models._
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
import uk.gov.hmrc.modules.uplift.views.html.BeforeYouStartView
import controllers.UserRequest
import uk.gov.hmrc.modules.uplift.controllers.UpliftJourneySwitch

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
    upliftJourneySwitch: UpliftJourneySwitch,
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
    upliftJourneySwitch.performSwitch(
      addApplicationProductionSwitch()(request),              // new uplift path
      successful(Ok(addApplicationStartPrincipalView()))      // existing uplift path
    )
  }
  
  def tenDaysWarning(): Action[AnyContent] = loggedInAction { implicit request => 
    successful(Ok(tenDaysWarningView())) 
  }
  
  def addApplicationName(environment: Environment): Action[AnyContent] = loggedInAction { implicit request =>
    val form = AddApplicationNameForm.form.fill(AddApplicationNameForm(""))
    successful(Ok(addApplicationNameView(form, environment)))
  }

  def progressOnUpliftJourney(sandboxAppId: ApplicationId): Action[AnyContent] = loggedInAction { implicit request =>
    upliftJourneySwitch.performSwitch(
      successful(Ok(beforeYouStartView(sandboxAppId))),        // new uplift path
      showConfirmSubscriptionsPage(sandboxAppId)(request)      // existing uplift path
    )
  }

  def soleApplicationToUpliftAction(appId: ApplicationId): Action[AnyContent] = loggedInAction { implicit request =>
    (for {
      upliftData          <- upliftLogic.aUsersSandboxAdminSummariesAndUpliftIds(request.userId)
    } yield upliftData.upliftableSummaries match {
      case summary :: Nil => progressOnUpliftJourney(summary.id)(request)
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

    // TODO - tidy as for comp
    upliftLogic.aUsersSandboxAdminSummariesAndUpliftIds(request.userId).flatMap { upliftData =>
      flowService.resetFlow(request.developerSession).flatMap { _ =>
        upliftData.upliftableApplicationIds.toList match {
          case Nil          => successful(BadRequest(Json.toJson(BadRequestError)))
          case appId :: Nil if !upliftData.hasAppsThatCannotBeUplifted => progressOnUpliftJourney(appId)(request)
          case _ => chooseApplicationToUplift(upliftData.upliftableSummaries, upliftData.hasAppsThatCannotBeUplifted)(request)
        }
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
      Redirect( uk.gov.hmrc.modules.uplift.controllers.routes.UpliftJourneyController.confirmApiSubscriptionsPage(sandboxAppId))
    }
  }

  def chooseApplicationToUpliftAction(): Action[AnyContent] = loggedInAction { implicit request =>

    def handleValidForm(validForm: ChooseApplicationToUpliftForm) =
      progressOnUpliftJourney(validForm.applicationId)(request)

    def handleInvalidForm(formWithErrors: Form[ChooseApplicationToUpliftForm]) = {
      upliftLogic.aUsersSandboxAdminSummariesAndUpliftIds(request.userId) flatMap { upliftData =>

      ( upliftData.upliftableApplicationIds.size, upliftData.hasAppsThatCannotBeUplifted) match {
          case (0, _)     => successful(BadRequest(Json.toJson(BadRequestError)))
          case (1, false) => successful(BadRequest(Json.toJson(BadRequestError)))
          case _  => successful(BadRequest(chooseApplicationToUpliftView(formWithErrors, upliftData.upliftableSummaries.toSeq, upliftData.hasAppsThatCannotBeUplifted)))
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
      applicationService.createForUser(CreateApplicationRequest.fromAddApplicationJourney(request.developerSession, form, environment))
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
          val alreadySelectedEmailPreferences: Boolean = appRequest.flash.get("emailPreferencesSelected").contains("true")
          subscriptionsNotInUserEmailPreferences(subscriptions.filter(_.subscribed), developerSession.developer.emailPreferences) map { missingSubscriptions =>

            if(alreadySelectedEmailPreferences || missingSubscriptions.isEmpty) {
              Ok(addApplicationSubordinateSuccessView(application.name, applicationId))
            } else {
              Redirect(controllers.profile.routes.EmailPreferences.selectApisFromSubscriptionsPage(applicationId))
                .flashing("missingSubscriptions" -> missingSubscriptions.mkString(","))
            }
          }
        }
        case PRODUCTION => successful(NotFound(errorHandler.notFoundTemplate(appRequest)))
      }
    }
  }

}
