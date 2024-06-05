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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.noapplications

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

import views.helper.EnvironmentNameService
import views.html.noapplications._

import play.api.data.Form
import play.api.data.Forms.{mapping, optional, text}
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}

import uk.gov.hmrc.thirdpartydeveloperfrontend.config.{ApplicationConfig, ErrorHandler}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.noapplications.NoApplications.NoApplicationsChoiceForm
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.{FormKeys, LoggedInController}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.FormExtensions._
import uk.gov.hmrc.thirdpartydeveloperfrontend.service._

object NoApplications {
  final case class NoApplicationsChoiceForm(choice: Option[String])

  object NoApplicationsChoiceForm {

    // def optional[A](mapping: Mapping[A]): Mapping[Option[A]] = OptionalMapping(mapping)

    def form: Form[NoApplicationsChoiceForm] = Form(mapping("choice" -> optional(text)
      .verifying(FormKeys.noApplicationsChoiceRequiredKey.value, s => s.isDefined))(NoApplicationsChoiceForm.apply)(NoApplicationsChoiceForm.unapply))

  }
}

@Singleton
class NoApplications @Inject() (
    val errorHandler: ErrorHandler,
    val sessionService: SessionService,
    val cookieSigner: CookieSigner,
    startUsingRestApisView: StartUsingRestApisView,
    noApplicationsChoiceView: NoApplicationsChoiceView,
    mcc: MessagesControllerComponents
  )(implicit val ec: ExecutionContext,
    val appConfig: ApplicationConfig,
    val environmentNameService: EnvironmentNameService
  ) extends LoggedInController(mcc) {

  def noApplicationsPage: Action[AnyContent] = loggedInAction { implicit request =>
    Future.successful(Ok(noApplicationsChoiceView(NoApplicationsChoiceForm.form)))
  }

  def noApplicationsAction: Action[AnyContent] = loggedInAction { implicit request =>
    NoApplicationsChoiceForm.form.bindFromRequest().fold(
      hasErrors => Future.successful(BadRequest(noApplicationsChoiceView(hasErrors))),
      formData =>
        formData.choice.getOrElse("") match {
          case "get-emails" =>
            Future.successful(Redirect(uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.profile.routes.EmailPreferencesController.emailPreferencesSummaryPage()))
          case "use-apis"   => Future.successful(Redirect(routes.NoApplications.startUsingRestApisPage()))
          case _            => Future.successful(InternalServerError(errorHandler.internalServerErrorTemplate))
        }
    )
  }

  def startUsingRestApisPage: Action[AnyContent] = loggedInAction { implicit request =>
    Future.successful(Ok(startUsingRestApisView()))
  }

}
