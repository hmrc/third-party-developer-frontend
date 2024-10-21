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

package uk.gov.hmrc.apiplatform.modules.uplift.controllers

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext
import scala.concurrent.Future.successful

import views.helper.IdFormatter
import views.html.checkpages.applicationcheck.UnauthorisedAppDetailsView

import play.api.data.Forms._
import play.api.data.{Form, FormError}
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import uk.gov.hmrc.play.bootstrap.controller.WithUnsafeDefaultFormBinding

import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models.SellResellOrDistribute
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.services.EitherTHelper
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models.Submission
import uk.gov.hmrc.apiplatform.modules.submissions.services.SubmissionService
import uk.gov.hmrc.apiplatform.modules.uplift.domain.models.ApiSubscriptions
import uk.gov.hmrc.apiplatform.modules.uplift.services.{GetProductionCredentialsFlowService, UpliftJourneyService}
import uk.gov.hmrc.apiplatform.modules.uplift.views.html._
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.{ApplicationConfig, ErrorHandler}
import uk.gov.hmrc.thirdpartydeveloperfrontend.connectors.ApmConnector
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.{APISubscriptions, ApplicationController, FormKeys}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.APISubscriptionStatus
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.TermsOfUseInvitation
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.BadRequestWithErrorMessage
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.{ApplicationActionService, ApplicationService, SessionService, TermsOfUseInvitationService}

object UpliftJourneyController {

  case class ChooseApplicationToUpliftForm(applicationId: ApplicationId)

  object ChooseApplicationToUpliftForm {

    val form: Form[ChooseApplicationToUpliftForm] = Form(
      mapping(
        "applicationId" -> nonEmptyText.transform[ApplicationId](text => ApplicationId(UUID.fromString(text)), id => id.toString())
      )(ChooseApplicationToUpliftForm.apply)(ChooseApplicationToUpliftForm.unapply)
    )
  }

  case class SellResellOrDistributeForm(answer: Option[String] = Some(""))

  object SellResellOrDistributeForm {

    def form: Form[SellResellOrDistributeForm] = Form(
      mapping(
        "answer" -> optional(text).verifying(FormKeys.sellResellOrDistributeConfirmNoChoiceKey.value, s => s.isDefined)
      )(SellResellOrDistributeForm.apply)(SellResellOrDistributeForm.unapply)
    )
  }
}

@Singleton
class UpliftJourneyController @Inject() (
    val errorHandler: ErrorHandler,
    val sessionService: SessionService,
    val applicationActionService: ApplicationActionService,
    val applicationService: ApplicationService,
    val submissionService: SubmissionService,
    val termsOfUseInvitationService: TermsOfUseInvitationService,
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
    unauthorisedAppDetailsView: UnauthorisedAppDetailsView
  )(implicit val ec: ExecutionContext,
    val appConfig: ApplicationConfig
  ) extends ApplicationController(mcc)
    with WithUnsafeDefaultFormBinding {

  import UpliftJourneyController._

  val sellResellOrDistributeForm: Form[SellResellOrDistributeForm] = SellResellOrDistributeForm.form

  private val exec = ec
  private val ET   = new EitherTHelper[Result] { implicit val ec: ExecutionContext = exec }

  def confirmApiSubscriptionsPage(sandboxAppId: ApplicationId): Action[AnyContent] = whenTeamMemberOnApp(sandboxAppId) { implicit request =>
    for {
      (data, canChange) <- upliftJourneyService.apiSubscriptionData(sandboxAppId, request.userSession, request.subscriptions)
    } yield Ok(confirmApisView(sandboxAppId, data, canChange))
  }

  def confirmApiSubscriptionsAction(sandboxAppId: ApplicationId): Action[AnyContent] = whenTeamMemberOnApp(sandboxAppId) { implicit request =>
    val failed = (msg: String) => successful(BadRequestWithErrorMessage(msg))

    val success = (upliftedAppId: ApplicationId) => {
      successful(Redirect(uk.gov.hmrc.apiplatform.modules.submissions.controllers.routes.ProdCredsChecklistController.productionCredentialsChecklistPage(upliftedAppId)))
    }

    upliftJourneyService.confirmAndUplift(sandboxAppId, request.userSession).flatMap(_.fold(failed, success))
  }

  def changeApiSubscriptions(sandboxAppId: ApplicationId): Action[AnyContent] = whenTeamMemberOnApp(sandboxAppId) { implicit request =>
    for {
      updatedSubscriptions <- upliftJourneyService.changeApiSubscriptions(sandboxAppId, request.userSession, request.subscriptions)
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
        .view.mapValues(_.head == "true").toMap
        .map {
          case (name, isSubscribed) => (name.replace("-subscribed", "") -> isSubscribed)
        }

    lazy val atLeastOneSubscription = formSubmittedSubscriptions.values.exists(identity)

    if (atLeastOneSubscription) {
      for {
        upliftableApiIds <- apmConnector.fetchUpliftableSubscriptions(sandboxAppId)
        apiLookups        = upliftableApiIds.map(id => IdFormatter.identifier(id) -> id).toMap
        newFlow           = ApiSubscriptions(
                              formSubmittedSubscriptions.map {
                                case (id, onOff) => apiLookups(id) -> onOff
                              }
                            )
        _                 = flowService.storeApiSubscriptions(newFlow, request.userSession)
      } yield Redirect(uk.gov.hmrc.apiplatform.modules.uplift.controllers.routes.UpliftJourneyController.confirmApiSubscriptionsPage(sandboxAppId))
    } else {
      val errorForm = DummySubscriptionsForm.form.withError(FormError("apiSubscriptions", "error.turnoffapis.requires.at.least.one"))
      for {
        flow                 <- flowService.fetchFlow(request.userSession)
        upliftableApiIds     <- apmConnector.fetchUpliftableSubscriptions(sandboxAppId)
        subscriptionFlow      = flow.apiSubscriptions.getOrElse(ApiSubscriptions())
        sandboxSubscribedApis = request.subscriptions
                                  .filter(s => upliftableApiIds.contains(s.apiIdentifier))
                                  .map(setSubscribedStatusFromFlow(subscriptionFlow))
      } yield Ok(turnOffApisMasterView(request.application.id, request.role, APISubscriptions.groupSubscriptionsByServiceName(sandboxSubscribedApis), errorForm))
    }
  }

  def sellResellOrDistributeYourSoftware(sandboxAppId: ApplicationId): Action[AnyContent] = whenTeamMemberOnApp(sandboxAppId) { implicit request =>
    for {
      sellResellOrDistribute <- flowService.findSellResellOrDistribute(request.userSession)
      form                    =
        sellResellOrDistribute.fold[Form[SellResellOrDistributeForm]](sellResellOrDistributeForm)(x => sellResellOrDistributeForm.fill(SellResellOrDistributeForm(Some(x.answer))))
    } yield Ok(sellResellOrDistributeSoftwareView(sandboxAppId, form))
  }

  def sellResellOrDistributeYourSoftwareAction(appId: ApplicationId): Action[AnyContent] = whenTeamMemberOnApp(appId) { implicit request =>
    def storeResultAndGotoApiSubscriptionsPage(ans: String) =
      for {
        _ <- flowService.storeSellResellOrDistribute(SellResellOrDistribute(ans), request.userSession)
        _ <- upliftJourneyService.storeDefaultSubscriptionsInFlow(appId, request.userSession)
      } yield Redirect(uk.gov.hmrc.apiplatform.modules.uplift.controllers.routes.UpliftJourneyController.confirmApiSubscriptionsPage(appId))

    def createSubmissionAndGotoQuestionnairePage(ans: String) =
      for {
        _ <- flowService.storeSellResellOrDistribute(SellResellOrDistribute(ans), request.userSession)
        _ <- upliftJourneyService.createNewSubmission(appId, request.application, request.userSession)
      } yield Redirect(uk.gov.hmrc.apiplatform.modules.submissions.controllers.routes.ProdCredsChecklistController.productionCredentialsChecklistPage(appId))

    def handleInvalidForm(formWithErrors: Form[SellResellOrDistributeForm]) =
      successful(BadRequest(sellResellOrDistributeSoftwareView(appId, formWithErrors)))

    def handleValidForm(validForm: SellResellOrDistributeForm) = {
      validForm.answer match {
        case Some(answer) =>
          request.application.deployedTo match {
            case Environment.SANDBOX    => storeResultAndGotoApiSubscriptionsPage(answer)
            case Environment.PRODUCTION => createSubmissionAndGotoQuestionnairePage(answer)
          }

        case None => throw new IllegalStateException("Should never get here")
      }
    }

    sellResellOrDistributeForm.bindFromRequest().fold(handleInvalidForm, handleValidForm)
  }

  def beforeYouStart(sandboxAppId: ApplicationId): Action[AnyContent] = whenTeamMemberOnApp(sandboxAppId) { implicit request =>
    successful(Ok(beforeYouStartView(sandboxAppId, None)))
  }

  def agreeNewTermsOfUse(appId: ApplicationId): Action[AnyContent] = whenTeamMemberOnApp(appId) { implicit request =>
    lazy val showSubmission = (s: Submission) =>
      if (request.role.isAdministrator) {
        if (s.status.isReadOnly) {
          Redirect(uk.gov.hmrc.apiplatform.modules.submissions.controllers.routes.CredentialsRequestedController.credentialsRequestedPage(appId))
        } else {
          if (s.status.isAnsweredCompletely) {
            Redirect(uk.gov.hmrc.apiplatform.modules.submissions.controllers.routes.CheckAnswersController.checkAnswersPage(appId))
          } else {
            Redirect(uk.gov.hmrc.apiplatform.modules.submissions.controllers.routes.ProdCredsChecklistController.productionCredentialsChecklistPage(appId))
          }
        }
      } else {
        Ok(unauthorisedAppDetailsView(request.application.name, request.application.admins))
      }

    def showBeforeYouStart(invite: TermsOfUseInvitation) =
      if (request.role.isAdministrator) {
        val completeDate = DateTimeFormatter.ofPattern("dd MMMM yyyy").withZone(ZoneId.systemDefault()).format(invite.dueBy)
        Ok(beforeYouStartView(appId, Some(completeDate)))
      } else {
        Ok(unauthorisedAppDetailsView(request.application.name, request.application.admins))
      }

    (
      for {
        invitation <-
          ET.fromOptionF(termsOfUseInvitationService.fetchTermsOfUseInvitation(appId), BadRequest("This application has not been invited to complete the new terms of use"))
        submission <- ET.fromOptionF(submissionService.fetchLatestSubmission(appId), showBeforeYouStart(invitation))
      } yield submission
    )
      .fold[Result](identity, showSubmission)
  }

  def weWillCheckYourAnswers(appId: ApplicationId): Action[AnyContent] = whenTeamMemberOnApp(appId) { implicit request =>
    request.application.deployedTo match {
      case Environment.SANDBOX    => successful(Ok(weWillCheckYourAnswersView(appId)))
      case Environment.PRODUCTION =>
        successful(Redirect(uk.gov.hmrc.apiplatform.modules.uplift.controllers.routes.UpliftJourneyController.sellResellOrDistributeYourSoftware(appId)))
    }
  }
}

case class DummySubscriptionsForm(hasNonExampleSubscription: Boolean)

object DummySubscriptionsForm {

  def form: Form[DummySubscriptionsForm] = Form(
    mapping(
      "hasNonExampleSubscription" -> boolean
    )(DummySubscriptionsForm.apply)(DummySubscriptionsForm.unapply)
      .verifying("error.must.subscribe", x => x.hasNonExampleSubscription)
  )

  def form2: Form[DummySubscriptionsForm] = Form(
    mapping(
      "hasNonExampleSubscription" -> boolean
    )(DummySubscriptionsForm.apply)(DummySubscriptionsForm.unapply)
      .verifying("error.turnoffapis.requires.at.least.one", x => x.hasNonExampleSubscription)
  )
}
