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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.endpointauth

class NoAuthEndpointScenarioSpec extends EndpointScenarioSpec
    with IsNewJourneyStandardApplication
    with UserIsAdmin
    with AppDeployedToProductionEnvironment
    with AppHasProductionStatus
    with UserIsNotAuthenticated {

  override def getExpectedResponse(endpoint: Endpoint): Response = {
    endpoint match {
      case Endpoint("GET",  "/developer/login") => Success()
      case Endpoint("POST", "/developer/login") => Unauthorized()
      case Endpoint(_,      "/developer/registration") => getEndpointSuccessResponse(endpoint)
      case Endpoint("GET",  "/developer/login-totp") => Success()
      case Endpoint("POST", "/developer/login-totp") => Error("java.util.NoSuchElementException: None.get")
      case Endpoint("GET",  "/developer/application-verification") => Success()
      case Endpoint(_,      "/developer/login/2SV-help") => Success()
      case Endpoint("GET",  "/developer/login/2SV-help/complete") => Success()
      case Endpoint(_,      "/developer/confirmation") => Success()
      case Endpoint("GET",  "/developer/resend-confirmation") => Success()
      case Endpoint("GET",  "/developer/support/submitted") => Success()
      case Endpoint("GET",  "/developer/verification") => Success()
      case Endpoint("GET",  "/developer/resend-verification") => BadRequest()
      case Endpoint("GET",  "/developer/locked") => Locked()
      case Endpoint("GET",  "/developer/reset-password") => Redirect("/developer/reset-password/error")
      case Endpoint("POST", "/developer/reset-password") => Error("java.lang.RuntimeException: email not found in session")
      case Endpoint("GET",  "/developer/reset-password-link") => Redirect("/developer/reset-password")
      case Endpoint("GET",  "/developer/reset-password/error") => BadRequest()
      case Endpoint(_,      "/developer/forgot-password") => Success()
      case Endpoint("GET",  "/developer/user-navlinks") => Success()
      case Endpoint("GET",  "/developer/logout") => Success()
      case Endpoint("GET",  "/developer/partials/terms-of-use") => Success()
      case Endpoint(_,      "/developer/support") => getEndpointSuccessResponse(endpoint)
      case Endpoint("GET",  "/developer/assets/*file") => Success()
      case Endpoint(_,      "/developer/submissions/responsible-individual-verification") => Success()
      case _ => Redirect("/developer/login")
    }
  }

}
