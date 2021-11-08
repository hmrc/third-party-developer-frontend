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

package modules.uplift.controllers

import config.{ApplicationConfig, ErrorHandler}
import connectors.ApmConnector
import controllers.checkpages.{CanUseCheckActions, DummySubscriptionsForm}
import domain.models.apidefinitions.APISubscriptionStatus
import domain.models.applications.ApplicationId
import modules.uplift.domain.models.{ApiSubscriptions, ResponsibleIndividual, SellResellOrDistribute}
import play.api.data.Forms._
import play.api.data.{Form, FormError}
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import service.{ApplicationActionService, ApplicationService, SessionService}
import views.helper.IdFormatter
import modules.uplift.views.html._
import modules.uplift.services.GetProductionCredentialsFlowService

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}
import controllers.ApplicationController
import controllers.APISubscriptions
import play.api.data.Forms
import controllers.FormKeys
import modules.uplift.services.UpliftJourneyService
import config.UpliftJourneyConfig
import play.api.mvc.Request
import scala.util.Try
import config.{On, OnDemand}
import domain.models.controllers.BadRequestWithErrorMessage

object UpliftJourneyController {

  case class ChooseApplicationToUpliftForm(applicationId: ApplicationId)

  object ChooseApplicationToUpliftForm {
    val form: Form[ChooseApplicationToUpliftForm] = Form(
      mapping(
        "applicationId" -> nonEmptyText.transform[ApplicationId](ApplicationId(_), id => id.value)
      )(ChooseApplicationToUpliftForm.apply)(ChooseApplicationToUpliftForm.unapply)
    )
  }

  case class ResponsibleIndividualForm(fullName: String, emailAddress: String)

  object ResponsibleIndividualForm {
    import uk.gov.hmrc.emailaddress.EmailAddress
    import controllers.FormKeys._
    import controllers.textValidator

    lazy val fullnameValidator = textValidator(responsibleIndividualFullnameRequiredKey, fullnameMaxLengthKey, 100)

    lazy val emailValidator =
      Forms.text
        .verifying(emailaddressNotValidKey, email => EmailAddress.isValid(email) || email.length == 0)
        .verifying(emailMaxLengthKey, email => email.length <= 320)
        .verifying(responsibleIndividualEmailAddressRequiredKey, email => email.length > 0)

    def form: Form[ResponsibleIndividualForm] = Form(
      mapping(
        "fullName" -> fullnameValidator,
        "emailAddress" -> emailValidator
      )(ResponsibleIndividualForm.apply)(ResponsibleIndividualForm.unapply)
    )
  }

  case class SellResellOrDistributeForm(answer: Option[String] = Some(""))

  object SellResellOrDistributeForm {
    def form: Form[SellResellOrDistributeForm] = Form(
      mapping(
        "answer" -> optional(text).verifying(FormKeys.sellResellOrDistributeConfirmNoChoiceKey, s => s.isDefined)
      )(SellResellOrDistributeForm.apply)(SellResellOrDistributeForm.unapply)
    )
  }
}

@Singleton
class UpliftJourneyController @Inject() (val errorHandler: ErrorHandler,
                      val sessionService: SessionService,
                      val applicationActionService: ApplicationActionService,
                      val applicationService: ApplicationService,
                      upliftJourneyService: UpliftJourneyService,
                      mcc: MessagesControllerComponents,
                      val cookieSigner: CookieSigner,
                      confirmApisView: ConfirmApisView,
                      turnOffApisMasterView: TurnOffApisMasterView,
                      val apmConnector: ApmConnector,
                      responsibleIndividualView: ResponsibleIndividualView,
                      flowService: GetProductionCredentialsFlowService,
                      sellResellOrDistributeSoftwareView: SellResellOrDistributeSoftwareView,
                      upliftJourneySwitch: UpliftJourneySwitch)
                     (implicit val ec: ExecutionContext, val appConfig: ApplicationConfig)
  extends ApplicationController(mcc)
     with CanUseCheckActions {

  import UpliftJourneyController._

  val responsibleIndividualForm: Form[ResponsibleIndividualForm] = ResponsibleIndividualForm.form
  val sellResellOrDistributeForm: Form[SellResellOrDistributeForm] = SellResellOrDistributeForm.form

  def confirmApiSubscriptionsPage(sandboxAppId: ApplicationId): Action[AnyContent] = whenTeamMemberOnApp(sandboxAppId) { implicit request =>
    for {
      (data, canChange) <- upliftJourneyService.apiSubscriptionData(sandboxAppId, request.user, request.subscriptions)
    }
    yield  Ok(confirmApisView(sandboxAppId, data, canChange))
  }

  def confirmApiSubscriptionsAction(sandboxAppId: ApplicationId): Action[AnyContent] = whenTeamMemberOnApp(sandboxAppId) { implicit request =>

    val failed = (msg: String) => successful(BadRequestWithErrorMessage(msg))

    val success = (upliftedAppId: ApplicationId) => {
      upliftJourneySwitch.performSwitch(
            successful(Redirect(modules.submissions.controllers.routes.ProdCredsChecklistController.productionCredentialsChecklist(upliftedAppId))),  // new uplift path
            successful(Redirect(controllers.checkpages.routes.ApplicationCheck.requestCheckPage(upliftedAppId))                                       // existing uplift path
              .withSession(request.session - "subscriptions"))
      )
    }

    upliftJourneyService.confirmAndUplift(sandboxAppId, request.user, upliftJourneySwitch.shouldUseV2).flatMap(_.fold(failed, success))
  }

  def changeApiSubscriptions(sandboxAppId: ApplicationId): Action[AnyContent] = whenTeamMemberOnApp(sandboxAppId) { implicit request =>
    for {
      updatedSubscriptions <- upliftJourneyService.changeApiSubscriptions(sandboxAppId, request.user, request.subscriptions)
    } yield Ok(turnOffApisMasterView(request.application.id, request.role, APISubscriptions.groupSubscriptionsByServiceName(updatedSubscriptions), DummySubscriptionsForm.form))
  }

  def saveApiSubscriptionsSubmit(sandboxAppId: ApplicationId) = whenTeamMemberOnApp(sandboxAppId) { implicit request =>
   // TODO - already elsewhere
    def setSubscribedStatusFromFlow(apiSubscriptions: ApiSubscriptions)(apiSubscription: APISubscriptionStatus): APISubscriptionStatus = {
      apiSubscription.copy(subscribed = apiSubscriptions.isSelected(apiSubscription.apiIdentifier))
    }

    lazy val formSubmittedSubscriptions: Map[String, Boolean] =
      request.body.asFormUrlEncoded.get
      .filter(_._1.contains("subscribed"))
      .mapValues(_.head == "true")
      .map {
        case (name, isSubscribed) => (name.replace("-subscribed", "") -> isSubscribed)
      }

    lazy val atLeastOneSubscription  = formSubmittedSubscriptions.values.exists(_ == true)

    if(atLeastOneSubscription) {
      for {
        upliftableApiIds  <- apmConnector.fetchUpliftableSubscriptions(sandboxAppId)
        apiLookups         = upliftableApiIds.map( id => IdFormatter.identifier(id) -> id ).toMap
        newFlow            = ApiSubscriptions(
                               formSubmittedSubscriptions.map {
                                 case (id, onOff) => apiLookups(id) -> onOff
                               }
                             )
        _                  = flowService.storeApiSubscriptions(newFlow, request.user)
      } yield Redirect(modules.uplift.controllers.routes.UpliftJourneyController.confirmApiSubscriptionsPage(sandboxAppId))
    }
    else {
      val errorForm = DummySubscriptionsForm.form.withError(FormError("apiSubscriptions", "error.turnoffapis.requires.at.least.one"))
      for {
        flow                  <- flowService.fetchFlow(request.user)
        upliftableApiIds      <- apmConnector.fetchUpliftableSubscriptions(sandboxAppId)
        subscriptionFlow       = flow.apiSubscriptions.getOrElse(ApiSubscriptions())
        sandboxSubscribedApis  = request.subscriptions
                                  .filter(s => upliftableApiIds.contains(s.apiIdentifier))
                                  .map(setSubscribedStatusFromFlow(subscriptionFlow))
      } yield Ok(turnOffApisMasterView(request.application.id, request.role, APISubscriptions.groupSubscriptionsByServiceName(sandboxSubscribedApis), errorForm))
    }
  }

  def responsibleIndividual(sandboxAppId: ApplicationId): Action[AnyContent] = whenTeamMemberOnApp(sandboxAppId) { implicit request =>
    for {
      responsibleIndividual <- flowService.findResponsibleIndividual(request.user)
      form = responsibleIndividual.fold[Form[ResponsibleIndividualForm]](responsibleIndividualForm)(x => responsibleIndividualForm.fill(ResponsibleIndividualForm(x.fullName, x.emailAddress)))
    } yield Ok(responsibleIndividualView(sandboxAppId, form))
  }

  def responsibleIndividualAction(sandboxAppId: ApplicationId): Action[AnyContent] = whenTeamMemberOnApp(sandboxAppId) { implicit request =>

    def handleValidForm(form: ResponsibleIndividualForm): Future[Result] = {
      val responsibleIndividual = ResponsibleIndividual(form.fullName, form.emailAddress)
      for {
        _ <- flowService.storeResponsibleIndividual(responsibleIndividual, request.user)
      } yield Redirect(modules.uplift.controllers.routes.UpliftJourneyController.sellResellOrDistributeYourSoftware(sandboxAppId))
    }
  
    def handleInvalidForm(form: Form[ResponsibleIndividualForm]): Future[Result] = {
      successful(BadRequest(responsibleIndividualView(sandboxAppId, form)))
    }

    responsibleIndividualForm.bindFromRequest.fold(handleInvalidForm, handleValidForm)
  }

  def sellResellOrDistributeYourSoftware(sandboxAppId: ApplicationId): Action[AnyContent] = whenTeamMemberOnApp(sandboxAppId) { implicit request =>
    for {
      sellResellOrDistribute <- flowService.findSellResellOrDistribute(request.user)
      form                    = sellResellOrDistribute.fold[Form[SellResellOrDistributeForm]](sellResellOrDistributeForm)(x => sellResellOrDistributeForm.fill(SellResellOrDistributeForm(Some(x.answer))))
    } yield Ok(sellResellOrDistributeSoftwareView(sandboxAppId, form))
  }

  def sellResellOrDistributeYourSoftwareAction(sandboxAppId: ApplicationId): Action[AnyContent] = whenTeamMemberOnApp(sandboxAppId) { implicit request =>

    def handleInvalidForm(formWithErrors: Form[SellResellOrDistributeForm]) =
      Future(BadRequest(sellResellOrDistributeSoftwareView(sandboxAppId, formWithErrors)))

    def handleValidForm(validForm: SellResellOrDistributeForm) = {
      validForm.answer match {
        case Some(answer) =>
          for {
            _     <- flowService.storeSellResellOrDistribute(SellResellOrDistribute(answer), request.user)
            _     <- upliftJourneyService.storeDefaultSubscriptionsInFlow(sandboxAppId, request.user)
          } yield Redirect(modules.uplift.controllers.routes.UpliftJourneyController.confirmApiSubscriptionsPage(sandboxAppId))

        case None => throw new IllegalStateException("Should never get here")
      }
    }
    sellResellOrDistributeForm.bindFromRequest.fold(handleInvalidForm, handleValidForm)
  }
}

@Singleton
class UpliftJourneySwitch @Inject() (upliftJourneyConfig: UpliftJourneyConfig) {

  private def upliftJourneyTurnedOnInRequestHeader(implicit request: Request[_]): Boolean =
    request.headers.get("useNewUpliftJourney").fold(false) { setting =>
      Try(setting.toBoolean).getOrElse(false)
    }

  def shouldUseV2(implicit request: Request[_]): Boolean =
    upliftJourneyConfig.status match {
      case On => true
      case OnDemand if upliftJourneyTurnedOnInRequestHeader => true
      case _ => false
    }

  def performSwitch(newUpliftPath: => Future[Result], existingUpliftPath: => Future[Result])(implicit request: Request[_]): Future[Result] =
    if(shouldUseV2) newUpliftPath else existingUpliftPath
}
