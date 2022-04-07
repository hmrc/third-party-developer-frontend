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

package uk.gov.hmrc.apiplatform.modules.uplift.controllers

import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.ErrorHandler
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ApmConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.checkpages.{CanUseCheckActions, DummySubscriptionsForm}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.APISubscriptionStatus
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.ApplicationId
import uk.gov.hmrc.apiplatform.modules.uplift.domain.models.{ApiSubscriptions}
import play.api.data.Forms._
import play.api.data.{Form, FormError}
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.{ApplicationActionService, ApplicationService, SessionService}
import views.helper.IdFormatter
import uk.gov.hmrc.apiplatform.modules.uplift.views.html._
import uk.gov.hmrc.apiplatform.modules.uplift.services.GetProductionCredentialsFlowService
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.checkpages

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.ApplicationController
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.APISubscriptions
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.FormKeys
import uk.gov.hmrc.apiplatform.modules.uplift.services.UpliftJourneyService
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.UpliftJourneyConfig
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.{On, OnDemand}
import play.api.mvc.Request
import scala.util.Try
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.BadRequestWithErrorMessage
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.SellResellOrDistribute

object UpliftJourneyController {

  case class ChooseApplicationToUpliftForm(applicationId: ApplicationId)

  object ChooseApplicationToUpliftForm {
    val form: Form[ChooseApplicationToUpliftForm] = Form(
      mapping(
        "applicationId" -> nonEmptyText.transform[ApplicationId](ApplicationId(_), id => id.value)
      )(ChooseApplicationToUpliftForm.apply)(ChooseApplicationToUpliftForm.unapply)
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
                      flowService: GetProductionCredentialsFlowService,
                      sellResellOrDistributeSoftwareView: SellResellOrDistributeSoftwareView,
                      weWillCheckYourAnswersView: WeWillCheckYourAnswersView,
                      beforeYouStartView: BeforeYouStartView,
                      upliftJourneySwitch: UpliftJourneySwitch)
                     (implicit val ec: ExecutionContext, val appConfig: ApplicationConfig)
  extends ApplicationController(mcc)
     with CanUseCheckActions {

  import UpliftJourneyController._

  val sellResellOrDistributeForm: Form[SellResellOrDistributeForm] = SellResellOrDistributeForm.form

  def confirmApiSubscriptionsPage(sandboxAppId: ApplicationId): Action[AnyContent] = whenTeamMemberOnApp(sandboxAppId) { implicit request =>
    for {
      (data, canChange) <- upliftJourneyService.apiSubscriptionData(sandboxAppId, request.developerSession, request.subscriptions)
    }
    yield  Ok(confirmApisView(sandboxAppId, data, canChange))
  }

  def confirmApiSubscriptionsAction(sandboxAppId: ApplicationId): Action[AnyContent] = whenTeamMemberOnApp(sandboxAppId) { implicit request =>

    val failed = (msg: String) => successful(BadRequestWithErrorMessage(msg))

    val success = (upliftedAppId: ApplicationId) => {
      upliftJourneySwitch.performSwitch(
            successful(Redirect(uk.gov.hmrc.apiplatform.modules.submissions.controllers.routes.ProdCredsChecklistController.productionCredentialsChecklistPage(upliftedAppId))),  // new uplift path
            successful(Redirect(checkpages.routes.ApplicationCheck.requestCheckPage(upliftedAppId))                                       // existing uplift path
              .withSession(request.session - "subscriptions"))
      )
    }

    upliftJourneyService.confirmAndUplift(sandboxAppId, request.developerSession, upliftJourneySwitch.shouldUseV2).flatMap(_.fold(failed, success))
  }

  def changeApiSubscriptions(sandboxAppId: ApplicationId): Action[AnyContent] = whenTeamMemberOnApp(sandboxAppId) { implicit request =>
    for {
      updatedSubscriptions <- upliftJourneyService.changeApiSubscriptions(sandboxAppId, request.developerSession, request.subscriptions)
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
        _                  = flowService.storeApiSubscriptions(newFlow, request.developerSession)
      } yield Redirect(uk.gov.hmrc.apiplatform.modules.uplift.controllers.routes.UpliftJourneyController.confirmApiSubscriptionsPage(sandboxAppId))
    }
    else {
      val errorForm = DummySubscriptionsForm.form.withError(FormError("apiSubscriptions", "error.turnoffapis.requires.at.least.one"))
      for {
        flow                  <- flowService.fetchFlow(request.developerSession)
        upliftableApiIds      <- apmConnector.fetchUpliftableSubscriptions(sandboxAppId)
        subscriptionFlow       = flow.apiSubscriptions.getOrElse(ApiSubscriptions())
        sandboxSubscribedApis  = request.subscriptions
                                  .filter(s => upliftableApiIds.contains(s.apiIdentifier))
                                  .map(setSubscribedStatusFromFlow(subscriptionFlow))
      } yield Ok(turnOffApisMasterView(request.application.id, request.role, APISubscriptions.groupSubscriptionsByServiceName(sandboxSubscribedApis), errorForm))
    }
  }

  def sellResellOrDistributeYourSoftware(sandboxAppId: ApplicationId): Action[AnyContent] = whenTeamMemberOnApp(sandboxAppId) { implicit request =>
    for {
      sellResellOrDistribute <- flowService.findSellResellOrDistribute(request.developerSession)
      form                    = sellResellOrDistribute.fold[Form[SellResellOrDistributeForm]](sellResellOrDistributeForm)(x => sellResellOrDistributeForm.fill(SellResellOrDistributeForm(Some(x.answer))))
    } yield Ok(sellResellOrDistributeSoftwareView(sandboxAppId, form))
  }

  def sellResellOrDistributeYourSoftwareAction(sandboxAppId: ApplicationId): Action[AnyContent] = whenTeamMemberOnApp(sandboxAppId) { implicit request =>

    def handleInvalidForm(formWithErrors: Form[SellResellOrDistributeForm]) =
      successful(BadRequest(sellResellOrDistributeSoftwareView(sandboxAppId, formWithErrors)))

    def handleValidForm(validForm: SellResellOrDistributeForm) = {
      validForm.answer match {
        case Some(answer) =>
          for {
            _     <- flowService.storeSellResellOrDistribute(SellResellOrDistribute(answer), request.developerSession)
            _     <- upliftJourneyService.storeDefaultSubscriptionsInFlow(sandboxAppId, request.developerSession)
          } yield Redirect(uk.gov.hmrc.apiplatform.modules.uplift.controllers.routes.UpliftJourneyController.confirmApiSubscriptionsPage(sandboxAppId))

        case None => throw new IllegalStateException("Should never get here")
      }
    }

    sellResellOrDistributeForm.bindFromRequest.fold(handleInvalidForm, handleValidForm)
  }

  def beforeYouStart(sandboxAppId: ApplicationId): Action[AnyContent] = whenTeamMemberOnApp(sandboxAppId) { implicit request =>
    successful(Ok(beforeYouStartView(sandboxAppId)))
  }

  def weWillCheckYourAnswers(sandboxAppId: ApplicationId): Action[AnyContent] = whenTeamMemberOnApp(sandboxAppId) { implicit request =>
    successful(Ok(weWillCheckYourAnswersView(sandboxAppId)))
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
