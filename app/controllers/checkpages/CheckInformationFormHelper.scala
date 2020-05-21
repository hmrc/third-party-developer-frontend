/*
 * Copyright 2020 HM Revenue & Customs
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

package controllers.checkpages

import controllers.{ApplicationController, ApplicationRequest}
import domain.{CheckInformation, CheckInformationForm}
import play.api.data.Form

trait CheckInformationFormHelper {
  self: ApplicationController =>

  def createCheckFormForApplication(request: ApplicationRequest[_]): Form[CheckInformationForm] = {
    val application = request.application

    if (hasSubscriptionFields(request)) {
      ApplicationInformationForm.formWithSubscriptionConfiguration.fillAndValidate(
        CheckInformationForm.fromCheckInformation(application.checkInformation.getOrElse(CheckInformation()))
      )
    } else {
      ApplicationInformationForm.formWithoutSubscriptionConfiguration.fillAndValidate(
        CheckInformationForm.fromCheckInformation(application.checkInformation.getOrElse(CheckInformation()))
      )
    }
  }

}
