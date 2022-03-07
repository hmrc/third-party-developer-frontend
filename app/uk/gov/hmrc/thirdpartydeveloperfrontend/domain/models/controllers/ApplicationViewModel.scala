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

package uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers

import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.Application

case class ApplicationViewModel(application: Application, hasSubscriptionsFields: Boolean, hasPpnsFields: Boolean) {
  lazy val privacyPolicyInDesktopApp = application.privacyPolicyUrl match {
    case Some("desktop") => true
    case _ => false
  }
  lazy val termsAndConditionsInDesktopApp = application.termsAndConditionsUrl match {
    case Some("desktop") => true
    case _ => false
  }
}
