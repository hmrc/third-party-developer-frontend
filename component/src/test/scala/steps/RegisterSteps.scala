/*
 * Copyright 2025 HM Revenue & Customs
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

import play.api.http.Status

import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.apiplatform.modules.tpd.core.dto.RegistrationRequest

object RegisterSteps extends ComponentTestDeveloperBuilder with NavigationSugar {

  // ^I enter valid information for all fields:$
  def givenIEnterValidInformationForAllFields(registrationDetails: (String, String)*): Unit = {
    val data: Map[String, String] = registrationDetails.toMap
    DeveloperStub.register(createPayload(data), Status.CREATED)
    Form.populate(data)
  }

  def createPayload(data: Map[String, String]): RegistrationRequest = {
    RegistrationRequest(data("email address").toLaxEmail, data("password"), data("first name"), data("last name"))
  }

  // ^I expect a resend call from '(.*)'$
  def givenIExpectAResendCallFrom(email: String) = {
    DeveloperStub.setupResend(email.toLaxEmail, Status.NO_CONTENT)
  }

}
