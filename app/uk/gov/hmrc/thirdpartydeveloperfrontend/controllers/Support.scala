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

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import views.html.support.{ApiSupportPageDetailView, ApiSupportPageView, LandingPageView}
import views.html.{SupportEnquiryView, SupportThankyouView}

import play.api.data.{Form, FormError}
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}

import uk.gov.hmrc.apiplatform.modules.apis.domain.models.ServiceName
import uk.gov.hmrc.apiplatform.modules.common.services.ApplicationLogger
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.{ApplicationConfig, ErrorHandler}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.FormKeys.commentsSpamKey
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers.DeveloperSession
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
    apiSupportPageDetailView: ApiSupportPageDetailView,
    supportService: SupportService
  )(implicit val ec: ExecutionContext,
    val appConfig: ApplicationConfig
  ) extends BaseController(mcc) with ApplicationLogger {

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
      form.helpWithChoice match {
        case "api"         => Future.successful(Redirect(routes.Support.apiSupportPage))
        case "account"     => Future.successful(Ok(landingPageView(fullyloggedInDeveloper, NewSupportPageHelpChoiceForm.form)))
        case "application" => Future.successful(Ok(landingPageView(fullyloggedInDeveloper, NewSupportPageHelpChoiceForm.form)))
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
      apis <- supportService.fetchAllPublicApis()
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
        apis <- supportService.fetchAllPublicApis()
      } yield BadRequest(
        apiSupportPageView(
          fullyloggedInDeveloper,
          form,
          routes.Support.raiseSupportEnquiry(true).url,
          apis
        )
      )
    }

    def handleValidForm(form: ApiSupportForm): Future[Result] =
      form.helpWithApiChoice match {
        case "api-call" =>
          Future.successful(Redirect(routes.Support.apiSupportDetailsPage(form.apiName)))
        case _          => renderApiSupportPageErrorView(ApiSupportForm.form.withError("error", "Error"))
      }

    def handleInvalidForm(formWithErrors: Form[ApiSupportForm]): Future[Result] =
      renderApiSupportPageErrorView(formWithErrors)

    ApiSupportForm.form.bindFromRequest().fold(handleInvalidForm, handleValidForm)
  }

  def apiSupportDetailsPage(apiServiceName: String): Action[AnyContent] = maybeAtLeastPartLoggedInEnablingMfa { implicit request =>
    def renderSupportDetailsPage(apiServiceName: String) =
      Ok(
        apiSupportPageDetailView(
          fullyloggedInDeveloper,
          ApiSupportDetailsForm.form,
          routes.Support.apiSupportAction.url,
          apiServiceName
        )
      )

    supportService.fetchApiDefinition(ServiceName(apiServiceName)).flatMap {
      case Right(api) => Future.successful(renderSupportDetailsPage(api.name))
      case Left(_)    => Future.successful(BadRequest(errorHandler.badRequestTemplate))
    }
  }

  def apiSupportDetailsAction: Action[AnyContent] = maybeAtLeastPartLoggedInEnablingMfa { implicit request =>
    def renderApiSupportDetailsPageErrorView(form: Form[ApiSupportDetailsForm]) = {
      Future.successful(
        BadRequest(
          apiSupportPageDetailView(
            fullyloggedInDeveloper,
            form,
            routes.Support.apiSupportAction.url,
            form.data.getOrElse("apiName", "UNKNOWN API")
          )
        )
      )
    }

    def handleValidForm(form: ApiSupportDetailsForm): Future[Result] = {
      // Return to api support details page for now
      Future.successful(
        Ok(
          apiSupportPageDetailView(
            fullyloggedInDeveloper,
            ApiSupportDetailsForm.form,
            routes.Support.apiSupportAction.url,
            form.apiName
          )
        )
      )
    }

    def handleInvalidForm(formWithErrors: Form[ApiSupportDetailsForm]): Future[Result] = {
      renderApiSupportDetailsPageErrorView(formWithErrors)
    }

    ApiSupportDetailsForm.form.bindFromRequest().fold(handleInvalidForm, handleValidForm)
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
