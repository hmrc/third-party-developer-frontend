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

import java.time.Clock
import javax.inject.{Inject, Singleton}
import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}

import cats.data.OptionT
import views.html.checkpages.applicationcheck.UnauthorisedAppDetailsView
import views.html.manageapplication.ApplicationDetailsView

import play.api.libs.crypto.CookieSigner
import play.api.mvc._

import uk.gov.hmrc.apiplatform.modules.applications.access.domain.models.AccessType
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.State
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{ApplicationId, LaxEmailAddress}
import uk.gov.hmrc.apiplatform.modules.common.services.ClockNow
import uk.gov.hmrc.apiplatform.modules.submissions.domain.models.Submission
import uk.gov.hmrc.apiplatform.modules.submissions.services.SubmissionService
import uk.gov.hmrc.thirdpartydeveloperfrontend.config.{ApplicationConfig, ErrorHandler, FraudPreventionConfig}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.ApplicationDetailsSectionsController.{Agreement, TermsOfUseViewModel}
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.fraudprevention.FraudPreventionNavLinkHelper
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.TermsOfUseV2State._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.connectors.TermsOfUseInvitation
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.{TermsOfUseV2State, TermsOfUseVersion}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.services.TermsOfUseService
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.services.TermsOfUseService.TermsOfUseAgreementDetails
import uk.gov.hmrc.thirdpartydeveloperfrontend.service._

@Singleton
class MainApplicationDetailsController @Inject() (
    val errorHandler: ErrorHandler,
    val applicationService: ApplicationService,
    val applicationActionService: ApplicationActionService,
    val sessionService: SessionService,
    val fraudPreventionConfig: FraudPreventionConfig,
    val termsOfUseService: TermsOfUseService,
    submissionService: SubmissionService,
    termsOfUseInvitationService: TermsOfUseInvitationService,
    profileService: ProfileService,
    unauthorisedAppDetailsView: UnauthorisedAppDetailsView,
    mcc: MessagesControllerComponents,
    val cookieSigner: CookieSigner,
    val clock: Clock,
    applicationDetailsView: ApplicationDetailsView
  )(implicit val ec: ExecutionContext,
    val appConfig: ApplicationConfig
  ) extends ApplicationController(mcc)
    with FraudPreventionNavLinkHelper
    with ClockNow {

  def applicationDetails(applicationId: ApplicationId): Action[AnyContent] = whenTeamMemberOnApp(applicationId) { implicit request =>
    def appDetailsPage =
      buildTermsOfUseViewModel().map { termsOfUseViewModel =>
        Ok(applicationDetailsView(
          applicationViewModelFromApplicationRequest(),
          request.subscriptions,
          createOptionalFraudPreventionNavLinkViewModel(
            request.application,
            request.subscriptions,
            fraudPreventionConfig
          ),
          termsOfUseViewModel
        ))
      }

    request.application.state.name match {
      case State.TESTING =>
        lazy val oldJourney = BadRequest("You can no longer view or update an old production credentials request.")

        lazy val newUpliftJourney = (s: Submission) =>
          if (request.role.isAdministrator) {
            if (s.status.isAnsweredCompletely) {
              Redirect(uk.gov.hmrc.apiplatform.modules.submissions.controllers.routes.CheckAnswersController.checkAnswersPage(applicationId))
            } else {
              Redirect(uk.gov.hmrc.apiplatform.modules.submissions.controllers.routes.ProdCredsChecklistController.productionCredentialsChecklistPage(applicationId))
            }
          } else {
            Ok(unauthorisedAppDetailsView(request.application.name, request.application.admins))
          }

        OptionT(submissionService.fetchLatestSubmission(applicationId)).fold(oldJourney)(newUpliftJourney)

      case State.PENDING_RESPONSIBLE_INDIVIDUAL_VERIFICATION | State.PENDING_GATEKEEPER_APPROVAL | State.PENDING_REQUESTER_VERIFICATION => {
        lazy val oldJourney = BadRequest("You can no longer view or update an old production credentials request.")

        lazy val newUpliftJourney = (s: Submission) =>
          Redirect(uk.gov.hmrc.apiplatform.modules.submissions.controllers.routes.CredentialsRequestedController.credentialsRequestedPage(applicationId))

        OptionT(submissionService.fetchLatestSubmission(applicationId)).fold(oldJourney)(newUpliftJourney)
      }

      case State.PRE_PRODUCTION =>
        request.queryString.contains("forceAppDetails") match {
          case true  => appDetailsPage
          case false =>
            successful(Redirect(uk.gov.hmrc.apiplatform.modules.submissions.controllers.routes.StartUsingYourApplicationController.startUsingYourApplicationPage(applicationId)))
        }

      case State.PRODUCTION =>
        appDetailsPage

      case State.DELETED =>
        successful(BadRequest)
    }
  }

  private def buildTermsOfUseViewModel()(implicit request: ApplicationRequest[AnyContent]): Future[TermsOfUseViewModel] = {
    implicit val hc: uk.gov.hmrc.http.HeaderCarrier = super.hc(request)
    val application                                 = request.application
    val requiresTermsOfUse                          = !application.deployedTo.isSandbox && application.access.accessType == AccessType.STANDARD

    if (requiresTermsOfUse) {
      val latestTermsOfUseAgreementDetails = termsOfUseService.getAgreementDetails(application).lastOption
      val (appUsesOldVersion, agreement)   = buildAgreementData(latestTermsOfUseAgreementDetails)

      for {
        termsOfUseV2State <- buildV2TermsOfUseState(application.id)
      } yield {
        TermsOfUseViewModel(requiresTermsOfUse, appUsesOldVersion, agreement, termsOfUseV2State)
      }
    } else {
      Future.successful(TermsOfUseViewModel(false, false, None, None))
    }
  }

  private def buildAgreementData(latestTermsOfUseAgreementDetails: Option[TermsOfUseAgreementDetails]): (Boolean, Option[Agreement]) = {
    latestTermsOfUseAgreementDetails match {
      case Some(TermsOfUseAgreementDetails(emailAddress, maybeName, date, maybeVersionString)) =>
        val maybeVersion      = maybeVersionString.flatMap(TermsOfUseVersion.fromVersionString)
        val appUsesOldVersion = maybeVersion.contains(TermsOfUseVersion.OLD_JOURNEY)
        val agreement         = Some(Agreement(maybeName.getOrElse(emailAddress.text), date))
        (appUsesOldVersion, agreement)
      case _                                                                                   =>
        (false, None)
    }
  }

  private def buildV2TermsOfUseState(
      applicationId: ApplicationId
    )(implicit hc: uk.gov.hmrc.http.HeaderCarrier
    ): Future[Option[TermsOfUseV2State]] = {
    for {
      maybeInvitation <- termsOfUseInvitationService.fetchTermsOfUseInvitation(applicationId)
      maybeSubmission <- submissionService.fetchLatestSubmission(applicationId)
      maybeState      <- buildState(maybeInvitation, maybeSubmission)
    } yield maybeState
  }

  private def buildState(
      maybeInvitation: Option[TermsOfUseInvitation],
      maybeSubmission: Option[Submission]
    )(implicit hc: uk.gov.hmrc.http.HeaderCarrier
    ): Future[Option[TermsOfUseV2State]] = {
    (maybeInvitation, maybeSubmission) match {
      case (Some(invitation), None) =>
        Future.successful(Some(NotStarted(Some(invitation.dueBy))))

      case (None, None) =>
        Future.successful(Some(NotStarted(Some(instant))))

      case (maybeInv, Some(submission)) if submission.status.isCreated || submission.status.isAnswering =>
        buildStartedState(submission, maybeInv.map(_.dueBy).getOrElse(instant))

      case (_, Some(submission)) if submission.status.isSubmitted || submission.status.isFailed || submission.status.isWarnings || submission.status.isGrantedWithWarnings =>
        buildSubmittedState(submission)

      case (_, Some(submission)) if submission.status.isGranted =>
        buildApprovedState(submission)

      case (_, Some(_)) =>
        Future.successful(None)
    }
  }

  private def buildStartedState(submission: Submission, deadline: java.time.Instant)(implicit hc: uk.gov.hmrc.http.HeaderCarrier): Future[Option[TermsOfUseV2State]] = {
    val requestedByEmail = extractRequestedByFromHistory(submission)
    profileService.lookupDeveloperName(LaxEmailAddress(requestedByEmail)).map { maybeName =>
      Some(Started(maybeName.getOrElse(requestedByEmail), deadline))
    }
  }

  private def buildSubmittedState(submission: Submission)(implicit hc: uk.gov.hmrc.http.HeaderCarrier): Future[Option[TermsOfUseV2State]] = {
    extractSubmittedFromHistory(submission) match {
      case Some(submitted) =>
        profileService.lookupDeveloperName(LaxEmailAddress(submitted.requestedBy)).map { maybeName =>
          Some(Submitted(maybeName.getOrElse(submitted.requestedBy), submitted.timestamp))
        }
      case None            =>
        Future.successful(None)
    }
  }

  private def buildApprovedState(submission: Submission): Future[Option[TermsOfUseV2State]] = {
    extractSubmittedFromHistory(submission) match {
      case Some(submitted) =>
        Future.successful(Some(Approved(submitted.requestedBy, submitted.timestamp)))
      case None            =>
        Future.successful(None)
    }
  }

  private def extractRequestedByFromHistory(submission: Submission): String = {
    submission.latestInstance.statusHistory.toList
      .collectFirst {
        case created: Submission.Status.Created => created.requestedBy
      }
      .getOrElse("unknown")
  }

  private def extractSubmittedFromHistory(submission: Submission): Option[Submission.Status.Submitted] = {
    submission.latestInstance.statusHistory.toList
      .collectFirst {
        case submitted: Submission.Status.Submitted => submitted
      }
  }
}
