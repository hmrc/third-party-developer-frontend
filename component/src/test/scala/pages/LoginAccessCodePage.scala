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

import org.openqa.selenium.By

import uk.gov.hmrc.apiplatform.modules.tpd.mfa.domain.models.MfaType

case class LoginAccessCodePage(mfaId: String, mfaType: MfaType, headingVal: String) extends FormPage with SubmitButton {
  override val pageHeading: String = headingVal
  override val url: String         = s"${EnvConfig.host}/developer/login-mfa?mfaId=${mfaId}&mfaType=${mfaType.toString}"

  private val accessCodeField    = By.name("accessCode")
  private val rememberMeCheckbox = By.name("rememberMe")

  def enterAccessCode(accessCode: String, rememberMe: Boolean = false) = {
    sendKeys(accessCodeField, accessCode)
    if (rememberMe)
      selectCheckbox(rememberMeCheckbox)
    else
      deselectCheckbox(rememberMeCheckbox)
    click(submitButton)
  }
}
