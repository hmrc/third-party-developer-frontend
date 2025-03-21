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

package uk.gov.hmrc.apiplatform.modules.submissions.controllers

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.crypto.CookieSigner
import play.api.mvc.{MessagesControllerComponents, Result}
import uk.gov.hmrc.play.bootstrap.controller.WithUnsafeDefaultFormBinding

import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.common.services.EitherTHelper
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models.{ErrorDetails, ResponsibleIndividualVerification}
import uk.gov.hmrc.apiplatform.modules.submissions.services.ResponsibleIndividualVerificationService
import uk.gov.hmrc.apiplatform.modules.submissions.views.html.{
  ResponsibleIndividualAcceptedView,
  ResponsibleIndividualDeclinedView,
  ResponsibleIndividualErrorView,
  VerifyResponsibleIndividualView
}
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.{ApplicationConfig, ErrorHandler}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.ApplicationController
import uk.gov.hmrc.thirdpartydeveloperfrontend.service.{ApplicationActionService, ApplicationService, SessionService}

object VerifyResponsibleIndividualController {

  case class ViewModel(
      appId: ApplicationId,
      appName: String,
      code: String
    )

  case class HasVerifiedForm(verified: String)

  val hasVerifiedForm: Form[HasVerifiedForm] = Form(
    mapping(
      "verified" -> nonEmptyText
    )(HasVerifiedForm.apply)(HasVerifiedForm.unapply)
  )
}

@Singleton
class VerifyResponsibleIndividualController @Inject() (
    val errorHandler: ErrorHandler,
    val cookieSigner: CookieSigner,
    val sessionService: SessionService,
    val applicationActionService: ApplicationActionService,
    val applicationService: ApplicationService,
    mcc: MessagesControllerComponents,
    responsibleIndividualVerificationService: ResponsibleIndividualVerificationService,
    verifyResponsibleIndividualView: VerifyResponsibleIndividualView,
    responsibleIndividualAcceptedView: ResponsibleIndividualAcceptedView,
    responsibleIndividualDeclinedView: ResponsibleIndividualDeclinedView,
    responsibleIndividualErrorView: ResponsibleIndividualErrorView
  )(implicit val ec: ExecutionContext,
    val appConfig: ApplicationConfig
  ) extends ApplicationController(mcc)
    with EitherTHelper[String]
    with WithUnsafeDefaultFormBinding {

  import cats.instances.future.catsStdInstancesForFuture

  private val exec                        = ec
  private val ET                          = new EitherTHelper[Result] { implicit val ec: ExecutionContext = exec }
  private val noRIVerificationRecordError = "This page has expired"

  def verifyPage(code: String) = Action.async { implicit request =>
    lazy val success = (riVerification: ResponsibleIndividualVerification) =>
      Ok(verifyResponsibleIndividualView(
        VerifyResponsibleIndividualController.ViewModel(riVerification.applicationId, riVerification.applicationName.value, code),
        VerifyResponsibleIndividualController.hasVerifiedForm
      ))

    (
      for {
        // Call into TPA to get details from the code supplied - error if not found
        riVerification <- ET.fromOptionF(
                            responsibleIndividualVerificationService.fetchResponsibleIndividualVerification(code),
                            BadRequest(responsibleIndividualErrorView(noRIVerificationRecordError))
                          )
      } yield success(riVerification)
    ).fold(identity(_), identity(_))
  }

  def verifyAction(code: String) = Action.async { implicit request =>
    def getVerified(verifyAnswer: String): Boolean = {
      verifyAnswer == "yes"
    }

    def handleValidForm(form: VerifyResponsibleIndividualController.HasVerifiedForm) = {
      val verified: Boolean = getVerified(form.verified)
      if (verified) {
        responsibleIndividualVerificationService.accept(code)
          .map(_ match {
            case Right(riVerification)      =>
              Ok(responsibleIndividualAcceptedView(VerifyResponsibleIndividualController.ViewModel(riVerification.applicationId, riVerification.applicationName.value, code)))
            case Left(ErrorDetails(_, msg)) => Ok(responsibleIndividualErrorView(msg))
          })
      } else {
        responsibleIndividualVerificationService.decline(code)
          .map(_ match {
            case Right(riVerification)      =>
              Ok(responsibleIndividualDeclinedView(VerifyResponsibleIndividualController.ViewModel(riVerification.applicationId, riVerification.applicationName.value, code)))
            case Left(ErrorDetails(_, msg)) => Ok(responsibleIndividualErrorView(msg))
          })
      }
    }

    def handleInvalidForm(form: Form[VerifyResponsibleIndividualController.HasVerifiedForm]) = {
      lazy val formValidationError = (riVerification: ResponsibleIndividualVerification) =>
        BadRequest(verifyResponsibleIndividualView(VerifyResponsibleIndividualController.ViewModel(riVerification.applicationId, riVerification.applicationName.value, code), form))

      (
        for {
          // Call into TPA to get details from the code supplied - error if not found
          riVerification <- ET.fromOptionF(
                              responsibleIndividualVerificationService.fetchResponsibleIndividualVerification(code),
                              BadRequest(responsibleIndividualErrorView(noRIVerificationRecordError))
                            )
        } yield formValidationError(riVerification)
      ).fold(identity(_), identity(_))
    }

    VerifyResponsibleIndividualController.hasVerifiedForm.bindFromRequest().fold(handleInvalidForm, handleValidForm)
  }
}
