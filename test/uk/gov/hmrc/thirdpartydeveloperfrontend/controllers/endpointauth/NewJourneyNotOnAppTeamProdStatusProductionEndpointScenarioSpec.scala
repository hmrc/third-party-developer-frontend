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

class NewJourneyNotOnAppTeamProdStatusProductionEndpointScenarioSpec extends EndpointScenarioSpec
  with IsNewJourneyStandardApplication
  with UserIsNotOnApplicationTeam
  with UserIsAuthenticated
  with AppDeployedToProductionEnvironment
  with AppHasProductionStatus {

  override def getExpectedResponse(endpoint: Endpoint): Response = {
    endpoint match {
      case Endpoint("GET",  "/developer/application-verification") => Success()
      case Endpoint("GET",  "/developer/applications") => Error("uk.gov.hmrc.http.NotFoundException: Role not found")
      case Endpoint("GET",  "/developer/applications/access-token") => Success()
      case Endpoint(_,      "/developer/applications/add/:environment/name") => Success()
      case Endpoint("GET",  "/developer/applications/add/:id") => Error("uk.gov.hmrc.http.NotFoundException: Role not found")
      case Endpoint("GET",  "/developer/applications/add/production") => Error("uk.gov.hmrc.http.NotFoundException: Role not found")
      case Endpoint("GET",  "/developer/applications/add/production/10-days") => Success()
      case Endpoint("GET",  "/developer/applications/add/sandbox") => Success()
      case Endpoint("GET",  "/developer/applications/add/switch") => Error("uk.gov.hmrc.http.NotFoundException: Role not found")
      case Endpoint("POST", "/developer/applications/add/switch") => Redirect(s"/developer/applications/${applicationId.value}/before-you-start")
      case Endpoint("GET",  "/developer/applications/using-privileged-application-credentials") => Success()
      case Endpoint("GET",  "/developer/assets/*file") => Success()
      case Endpoint("GET",  "/developer/confirmation") => Success()
      case Endpoint(_,      "/developer/forgot-password") => getEndpointSuccessResponse(endpoint)
      case Endpoint("GET",  "/developer/keep-alive") => Success()
      case Endpoint("GET",  "/developer/locked") => Locked()
      case Endpoint(_,      "/developer/login") => getEndpointSuccessResponse(endpoint)
      case Endpoint(_,      "/developer/login-totp") => getEndpointSuccessResponse(endpoint)
      case Endpoint(_,      "/developer/login/2SV-help") => Redirect(s"/developer/applications")
      case Endpoint("GET",  "/developer/login/2SV-help/complete") => Redirect(s"/developer/applications")
      case Endpoint("GET",  "/developer/login/2SV-not-set") => Success()
      case Endpoint("GET",  "/developer/login/2sv-recommendation") => Success()
      case Endpoint("GET",  "/developer/logout") => Success()
      case Endpoint(_,      "/developer/logout/survey") => getEndpointSuccessResponse(endpoint)
      case Endpoint(_,      "/developer/no-applications") => getEndpointSuccessResponse(endpoint)
      case Endpoint("GET",  "/developer/no-applications") => Success()
      case Endpoint("GET" , "/developer/no-applications-start") => Success()
      case Endpoint("GET",  "/developer/partials/terms-of-use") => Success()
      case Endpoint(_,      "/developer/profile/") => Success()
      case Endpoint("GET",  "/developer/profile/change") => Success()
      case Endpoint(_,      "/developer/profile/delete") => Success()
      case Endpoint(_, path) if path.startsWith("/developer/profile/email-preferences") => getEndpointSuccessResponse(endpoint)
      case Endpoint(_,      "/developer/profile/password") => Success()
      case Endpoint(_, path) if path.startsWith("/developer/profile/protect-account") => getEndpointSuccessResponse(endpoint)
      case Endpoint("POST", "/developer/registration") => BadRequest()
      case Endpoint("GET",  "/developer/registration") => Redirect(s"/developer/applications")
      case Endpoint("GET",  "/developer/resend-confirmation") => Success()
      case Endpoint("GET",  "/developer/resend-verification") => Redirect(s"/developer/confirmation")
      case Endpoint(_,      "/developer/reset-password") => Success()
      case Endpoint("GET",  "/developer/reset-password-link") => Redirect(s"/developer/reset-password")
      case Endpoint("GET",  "/developer/reset-password/error") => BadRequest()
      case Endpoint(_,      "/developer/submissions/responsible-individual-verification") => Success()
      case Endpoint(_,      "/developer/support") => getEndpointSuccessResponse(endpoint)
      case Endpoint("GET",  "/developer/support/submitted") => Success()
      case Endpoint("GET",  "/developer/user-navlinks") => Success()
      case Endpoint("GET",  "/developer/verification") => Success()

      case _ => NotFound()
    }
  }

}
