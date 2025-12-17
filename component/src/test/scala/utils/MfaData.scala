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

trait MfaData extends ComponentTestDeveloperBuilder {
  val accessCode        = "123456"
  val mobileNumber      = "+447890123456"
  val authAppMfaId      = authenticatorAppMfaDetails.id
  val smsMfaId          = smsMfaDetails.id
  val deviceCookieName  = "DEVICE_SESS_ID"
  val deviceCookieValue = "a6b5b0cca96fef5ffc66edffd514a9239b46b4e869fc10f6-9193-42b4-97f2-87886c972ad4"
}

object MfaData extends MfaData
