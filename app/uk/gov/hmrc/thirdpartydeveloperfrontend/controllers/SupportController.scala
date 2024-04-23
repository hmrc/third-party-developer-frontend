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

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import views.html.support.{ApiSupportPageView, LandingPageView, SupportPageConfirmationView, SupportPageDetailView}
import views.html.{SupportEnquiryView, SupportThankyouView}

import play.api.data.{Form, FormError}
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}

import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ServiceName
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.{ApplicationConfig, ErrorHandler}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.DeveloperSession
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.flows.SupportFlow
import uk.gov.hmrc.thirdpartydeveloperfrontend.security.SupportCookie
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.{DeskproService, SessionService, SupportService}
import support.NewSupportPageHelpChoiceForm

@Singleton
class SupportController @Inject() (
    val deskproService: DeskproService,
    val sessionService: SessionService,
    val errorHandler: ErrorHandler,
    mcc: MessagesControllerComponents,
    val cookieSigner: CookieSigner,
    supportEnquiryView: SupportEnquiryView,
    supportThankyouView: SupportThankyouView,
    landingPageView: LandingPageView,
    apiSupportPageView: ApiSupportPageView,
    supportPageDetailView: SupportPageDetailView,
    supportPageConfirmationView: SupportPageConfirmationView,
    supportService: SupportService
  )(implicit val ec: ExecutionContext,
    val appConfig: ApplicationConfig
  ) extends BaseController(mcc) with SupportCookie {

  private def fullyloggedInDeveloper(implicit request: MaybeUserRequest[AnyContent]): Option[DeveloperSession] =
    request.developerSession.filter(_.loggedInState.isLoggedIn)

  def chooseSupportOptionAction: Action[AnyContent] = maybeAtLeastPartLoggedInEnablingMfa { implicit request =>
    def handleValidForm(form: NewSupportPageHelpChoiceForm): Future[Result] = {
      val sessionId = extractSupportSessionIdFromCookie(request).getOrElse(UUID.randomUUID().toString)
      supportService.createFlow(sessionId, form.helpWithChoice)
      form.helpWithChoice match {
        case SupportData.UsingAnApi.id => Future.successful(withSupportCookie(Redirect(routes.SupportController.apiSupportPage()), sessionId))
        case _                     => Future.successful(withSupportCookie(Redirect(support.routes.DetailsController.supportDetailsPage()), sessionId))
      }
    }

    def handleInvalidForm(formWithErrors: Form[NewSupportPageHelpChoiceForm]): Future[Result] = {
      Future.successful(BadRequest(landingPageView(fullyloggedInDeveloper, formWithErrors)))
    }

    support.NewSupportPageHelpChoiceForm.form.bindFromRequest().fold(handleInvalidForm, handleValidForm)
  }

  def apiSupportPage: Action[AnyContent] = maybeAtLeastPartLoggedInEnablingMfa { implicit request =>
    for {
      apis <- supportService.fetchAllPublicApis(request.developerSession.map(_.developer.userId))
    } yield Ok(
      apiSupportPageView(
        fullyloggedInDeveloper,
        ApiSupportForm.form,
        support.routes.EnquiryController.raiseSupportEnquiry(true).url,
        apis
      )
    )
  }

  def apiSupportAction: Action[AnyContent] = maybeAtLeastPartLoggedInEnablingMfa { implicit request =>
    def renderApiSupportPageErrorView(form: Form[ApiSupportForm]) = {
      for {
        apis <- supportService.fetchAllPublicApis(request.developerSession.map(_.developer.userId))
      } yield BadRequest(
        apiSupportPageView(
          fullyloggedInDeveloper,
          form,
          support.routes.EnquiryController.raiseSupportEnquiry(true).url,
          apis
        )
      )
    }

    // def redirectToPrivateApiDocPageOnFlow(sessionId: String, onFlow: => Future[Either[Throwable, SupportFlow]]): Future[Result] =
    //   onFlow.flatMap {
    //     case Right(_) => Future.successful(withSupportCookie(Redirect(routes.SupportData.supportPrivateApiDocumentationPage()), sessionId))
    //     case Left(_)  => renderApiSupportPageErrorView(ApiSupportForm.form.withError("error", "Error"))
    //   }

    def redirectToDetailsPageOnFlow(sessionId: String, onFlow: => Future[Either[Throwable, SupportFlow]]): Future[Result] =
      onFlow.flatMap {
        case Right(_) => Future.successful(withSupportCookie(Redirect(support.routes.DetailsController.supportDetailsPage()), sessionId))
        case Left(_)  => renderApiSupportPageErrorView(ApiSupportForm.form.withError("error", "Error"))
      }

    def clearAnyApiChoiceAndRedirect(): Future[Result] = {
      val sessionId = extractSupportSessionIdFromCookie(request).getOrElse(UUID.randomUUID().toString)
      redirectToDetailsPageOnFlow(sessionId, supportService.clearApiChoice(sessionId))
    }

    def updateFlowAndRedirect(usingApiSubSelection: String, apiName: String): Future[Result] = {
      val sessionId = extractSupportSessionIdFromCookie(request).getOrElse(UUID.randomUUID().toString)
      redirectToDetailsPageOnFlow(sessionId, supportService.updateApiChoice(sessionId, ServiceName(apiName), usingApiSubSelection))
    }

    def handleValidForm(form: ApiSupportForm): Future[Result] = {
      form.helpWithApiChoice match {
        case SupportData.MakingAnApiCall.id         => updateFlowAndRedirect(SupportData.MakingAnApiCall.id, form.apiNameForCall)
        case SupportData.GettingExamples.id         => updateFlowAndRedirect(SupportData.GettingExamples.id, form.apiNameForExamples)
        case SupportData.ReportingDocumentation.id  => updateFlowAndRedirect(SupportData.ReportingDocumentation.id, form.apiNameForReporting)
        case SupportData.PrivateApiDocumentation.id => updateFlowAndRedirect(SupportData.PrivateApiDocumentation.id, form.apiNameForReporting)
        case _                                  => clearAnyApiChoiceAndRedirect()
      }
    }

    def handleInvalidForm(formWithErrors: Form[ApiSupportForm]): Future[Result] =
      renderApiSupportPageErrorView(formWithErrors)

    ApiSupportForm.form.bindFromRequest().fold(handleInvalidForm, handleValidForm)
  }

  // def supportPrivateApiDocumentationPage(): Action[AnyContent] = maybeAtLeastPartLoggedInEnablingMfa { implicit request =>
  //   def renderPage(flow: SupportFlow) =
  //     Ok(
  //       supportPrivateApiDocumentationView(
  //         fullyloggedInDeveloper,
  //         ApiSupportDetailsForm.form,
  //         routes.SupportController.apiSupportAction().url,
  //         flow
  //       )
  //     )

  //   val sessionId = extractSupportSessionIdFromCookie(request).getOrElse(UUID.randomUUID().toString)
  //   supportService.getSupportFlow(sessionId).map(renderPage)
  // }

  def supportConfirmationPage(): Action[AnyContent] = maybeAtLeastPartLoggedInEnablingMfa { implicit request =>
    def renderSupportConfirmationPage(flow: SupportFlow) =
      Ok(
        supportPageConfirmationView(
          fullyloggedInDeveloper,
          flow
        )
      )

    extractSupportSessionIdFromCookie(request).map(sessionId => supportService.getSupportFlow(sessionId).map(renderSupportConfirmationPage)).getOrElse(Future.successful(
      Redirect(support.routes.EnquiryController.raiseSupportEnquiry(true))
    ))
  }

  def thankyou = maybeAtLeastPartLoggedInEnablingMfa { implicit request =>
    val displayName = fullyloggedInDeveloper.map(_.displayedName)
    Future.successful(Ok(supportThankyouView("Thank you", displayName)))
  }
}
