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
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.FormKeys.commentsSpamKey
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.DeveloperSession
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.flows.SupportFlow
import uk.gov.hmrc.thirdpartydeveloperfrontend.security.SupportCookie
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.{DeskproService, SessionService, SupportService}

@Singleton
class Support @Inject() (
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

  val supportForm: Form[SupportEnquiryForm] = SupportEnquiryForm.form

  private def fullyloggedInDeveloper(implicit request: MaybeUserRequest[AnyContent]): Option[DeveloperSession] =
    request.developerSession.filter(_.loggedInState.isLoggedIn)

  def raiseSupportEnquiry(useNewSupport: Boolean): Action[AnyContent] = maybeAtLeastPartLoggedInEnablingMfa { implicit request =>
    val prefilledForm = fullyloggedInDeveloper
      .fold(supportForm) { user =>
        supportForm.bind(Map("fullname" -> user.displayedName, "emailaddress" -> user.email.text)).discardingErrors
      }

    if (useNewSupport)
      Future.successful(Ok(landingPageView(fullyloggedInDeveloper, NewSupportPageHelpChoiceForm.form)))
    else
      Future.successful(Ok(supportEnquiryView(fullyloggedInDeveloper.map(_.displayedName), prefilledForm)))
  }

  def submitSupportEnquiry = maybeAtLeastPartLoggedInEnablingMfa { implicit request =>
    val requestForm = supportForm.bindFromRequest()
    val displayName = fullyloggedInDeveloper.map(_.displayedName)
    val userId      = fullyloggedInDeveloper.map(_.developer.userId)

    requestForm.fold(
      formWithErrors => {
        logSpamSupportRequest(formWithErrors)
        Future.successful(BadRequest(supportEnquiryView(displayName, formWithErrors)))
      },
      formData => deskproService.submitSupportEnquiry(userId, formData).map { _ => Redirect(routes.Support.thankyou.url, SEE_OTHER) }
    )
  }

  def chooseSupportOptionAction: Action[AnyContent] = maybeAtLeastPartLoggedInEnablingMfa { implicit request =>
    def handleValidForm(form: NewSupportPageHelpChoiceForm): Future[Result] = {
      val sessionId = extractSupportSessionIdFromCookie(request).getOrElse(UUID.randomUUID().toString)
      supportService.createFlow(sessionId, form.helpWithChoice)
      form.helpWithChoice match {
        case "api"         => Future.successful(withSupportCookie(Redirect(routes.Support.apiSupportPage), sessionId))
        case "account"     => Future.successful(Ok(landingPageView(fullyloggedInDeveloper, NewSupportPageHelpChoiceForm.form)))
        case "application" => Future.successful(Ok(landingPageView(fullyloggedInDeveloper, NewSupportPageHelpChoiceForm.form)))
        case "find-api"    => Future.successful(withSupportCookie(Redirect(routes.Support.supportDetailsPage), sessionId))
        case _             => Future.successful(BadRequest(landingPageView(fullyloggedInDeveloper, NewSupportPageHelpChoiceForm.form.withError("error", "Error"))))
      }
    }

    def handleInvalidForm(formWithErrors: Form[NewSupportPageHelpChoiceForm]): Future[Result] = {
      Future.successful(BadRequest(landingPageView(fullyloggedInDeveloper, formWithErrors)))
    }

    NewSupportPageHelpChoiceForm.form.bindFromRequest().fold(handleInvalidForm, handleValidForm)
  }

  def apiSupportPage: Action[AnyContent] = maybeAtLeastPartLoggedInEnablingMfa { implicit request =>
    for {
      apis <- supportService.fetchAllPublicApis(request.developerSession.map(_.developer.userId))
    } yield Ok(
      apiSupportPageView(
        fullyloggedInDeveloper,
        ApiSupportForm.form,
        routes.Support.raiseSupportEnquiry(true).url,
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
          routes.Support.raiseSupportEnquiry(true).url,
          apis
        )
      )
    }

    def updateFlowAndRedirect(apiName: String): Future[Result] = {
      val sessionId = extractSupportSessionIdFromCookie(request).getOrElse(UUID.randomUUID().toString)
      supportService.updateApiChoice(sessionId, ServiceName(apiName)) flatMap {
        case Right(_) => Future.successful(withSupportCookie(Redirect(routes.Support.supportDetailsPage), sessionId))
        case Left(_)  => renderApiSupportPageErrorView(ApiSupportForm.form.withError("error", "Error"))
      }
    }

    def handleValidForm(form: ApiSupportForm): Future[Result] =
      form.helpWithApiChoice match {
        case "api-call" => updateFlowAndRedirect(form.apiName)
        case _          => renderApiSupportPageErrorView(ApiSupportForm.form.withError("error", "Error"))
      }

    def handleInvalidForm(formWithErrors: Form[ApiSupportForm]): Future[Result] =
      renderApiSupportPageErrorView(formWithErrors)

    ApiSupportForm.form.bindFromRequest().fold(handleInvalidForm, handleValidForm)
  }

  def supportDetailsPage(): Action[AnyContent] = maybeAtLeastPartLoggedInEnablingMfa { implicit request =>
    def renderSupportDetailsPage(flow: SupportFlow) =
      Ok(
        supportPageDetailView(
          fullyloggedInDeveloper,
          ApiSupportDetailsForm.form,
          routes.Support.apiSupportAction.url,
          flow
        )
      )

    val sessionId = extractSupportSessionIdFromCookie(request).getOrElse(UUID.randomUUID().toString)
    supportService.getSupportFlow(sessionId).map(renderSupportDetailsPage)
  }

  def supportDetailsAction: Action[AnyContent] = maybeAtLeastPartLoggedInEnablingMfa { implicit request =>
    def renderApiSupportDetailsPageErrorView(flow: SupportFlow)(form: Form[ApiSupportDetailsForm]) = {
      Future.successful(
        BadRequest(
          supportPageDetailView(
            fullyloggedInDeveloper,
            form,
            routes.Support.apiSupportAction.url,
            flow
          )
        )
      )
    }

    def handleValidForm(sessionId: String, flow: SupportFlow)(form: ApiSupportDetailsForm): Future[Result] = {
      supportService.submitTicket(flow, form).map(_ =>
        withSupportCookie(Redirect(routes.Support.supportConfirmationPage), sessionId)
      )
    }

    def handleInvalidForm(flow: SupportFlow)(formWithErrors: Form[ApiSupportDetailsForm]): Future[Result] = {
      renderApiSupportDetailsPageErrorView(flow)(formWithErrors)
    }

    val sessionId = extractSupportSessionIdFromCookie(request).getOrElse(UUID.randomUUID().toString)

    supportService.getSupportFlow(sessionId).flatMap(flow =>
      ApiSupportDetailsForm.form.bindFromRequest().fold(handleInvalidForm(flow), handleValidForm(sessionId, flow))
    )
  }

  def supportConfirmationPage(): Action[AnyContent] = maybeAtLeastPartLoggedInEnablingMfa { implicit request =>
    def renderSupportConfirmationPage(flow: SupportFlow) =
      Ok(
        supportPageConfirmationView(
          fullyloggedInDeveloper,
          flow
        )
      )

    extractSupportSessionIdFromCookie(request).map(sessionId => supportService.getSupportFlow(sessionId).map(renderSupportConfirmationPage)).getOrElse(Future.successful(
      Redirect(routes.Support.raiseSupportEnquiry(true))
    ))
  }

  private def logSpamSupportRequest(form: Form[SupportEnquiryForm]) = {
    form.errors("comments").map((formError: FormError) => {
      if (formError.message == commentsSpamKey) {
        logger.info("Spam support request attempted")
      }
    })
  }

  def thankyou = maybeAtLeastPartLoggedInEnablingMfa { implicit request =>
    val displayName = fullyloggedInDeveloper.map(_.displayedName)
    Future.successful(Ok(supportThankyouView("Thank you", displayName)))
  }
}
