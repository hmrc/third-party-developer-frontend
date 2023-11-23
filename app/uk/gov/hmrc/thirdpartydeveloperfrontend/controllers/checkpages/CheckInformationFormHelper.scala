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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.checkpages

import play.api.data.Form
import play.api.data.Forms.{boolean, ignored, mapping}
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.CheckInformation

import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.{ApplicationController, ApplicationRequest}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.views.CheckInformationForm

trait CheckInformationFormHelper {
  self: ApplicationController =>

  def createCheckFormForApplication(request: ApplicationRequest[_]): Form[CheckInformationForm] = {
    val application = request.application

    if (request.hasSubscriptionFields) {
      formWithSubscriptionConfiguration.fill(
        CheckInformationForm.fromCheckInformation(application.checkInformation.getOrElse(CheckInformation()))
      )
    } else {
      formWithoutSubscriptionConfiguration.fill(
        CheckInformationForm.fromCheckInformation(application.checkInformation.getOrElse(CheckInformation()))
      )
    }
  }

  def validateCheckFormForApplication(request: ApplicationRequest[_]): Form[CheckInformationForm] = {
    val application = request.application

    if (request.hasSubscriptionFields) {
      formWithSubscriptionConfiguration.fillAndValidate(
        CheckInformationForm.fromCheckInformation(application.checkInformation.getOrElse(CheckInformation()))
      )
    } else {
      formWithoutSubscriptionConfiguration.fillAndValidate(
        CheckInformationForm.fromCheckInformation(application.checkInformation.getOrElse(CheckInformation()))
      )
    }
  }

  private def formWithoutSubscriptionConfiguration: Form[CheckInformationForm] = Form(
    mapping(
      "apiSubscriptionsCompleted"              -> boolean.verifying("api.subscriptions.required.field", subsConfirmed => subsConfirmed),
      "apiSubscriptionConfigurationsCompleted" -> ignored(false),
      "contactDetailsCompleted"                -> boolean.verifying("contact.details.required.field", cd => cd),
      "teamConfirmedCompleted"                 -> boolean.verifying("team.required.field", provided => provided),
      "confirmedNameCompleted"                 -> boolean.verifying("confirm.name.required.field", cn => cn),
      "providedPolicyURLCompleted"             -> boolean.verifying("privacy.links.required.field", provided => provided),
      "providedTermsAndConditionsURLCompleted" -> boolean.verifying("tnc.links.required.field", provided => provided),
      "termsOfUseAgreementsCompleted"          -> boolean.verifying("agree.terms.of.use.required.field", terms => terms)
    )(CheckInformationForm.apply)(CheckInformationForm.unapply)
  )

  private def formWithSubscriptionConfiguration: Form[CheckInformationForm] = Form(
    mapping(
      "apiSubscriptionsCompleted"              -> boolean.verifying("api.subscriptions.required.field", subsConfirmed => subsConfirmed),
      "apiSubscriptionConfigurationsCompleted" -> boolean.verifying(
        "api.subscription.configurations.required.field",
        subscriptionConfigurationConfirmed => subscriptionConfigurationConfirmed
      ),
      "contactDetailsCompleted"                -> boolean.verifying("contact.details.required.field", cd => cd),
      "teamConfirmedCompleted"                 -> boolean.verifying("team.required.field", provided => provided),
      "confirmedNameCompleted"                 -> boolean.verifying("confirm.name.required.field", cn => cn),
      "providedPolicyURLCompleted"             -> boolean.verifying("privacy.links.required.field", provided => provided),
      "providedTermsAndConditionsURLCompleted" -> boolean.verifying("tnc.links.required.field", provided => provided),
      "termsOfUseAgreementsCompleted"          -> boolean.verifying("agree.terms.of.use.required.field", terms => terms)
    )(CheckInformationForm.apply)(CheckInformationForm.unapply)
  )
}
