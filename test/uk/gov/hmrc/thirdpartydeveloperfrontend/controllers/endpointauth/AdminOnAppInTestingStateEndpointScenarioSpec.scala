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

class AdminOnAppInTestingStateEndpointScenarioSpec extends EndpointScenarioSpec
  with IsNewJourneyStandardApplicationWithoutSubmission
  with UserIsAdmin
  with UserIsAuthenticated
  with AppDeployedToSandboxEnvironment
  with AppHasTestingStatus {

  override def getExpectedResponse(endpoint: Endpoint): Response = {
    endpoint match {
      case Endpoint("GET",  "/developer/registration") => Redirect("/developer/applications")
      case Endpoint("POST", "/developer/registration") => BadRequest()
      case Endpoint("GET",  "/developer/reset-password/error") => BadRequest()
      case Endpoint("GET",  "/developer/applications/:id/team-members") => NotFound() // app is not approved
      case Endpoint("GET",  "/developer/applications/:id/team-members/add") => NotFound() // app is not approved
      case Endpoint("POST", "/developer/applications/:id/team-members/remove") => NotFound() // app is not approved
      case Endpoint("GET",  "/developer/applications/:id/team-members/:teamMemberHash/remove-confirmation") => NotFound() // app is not approved
      case Endpoint(_,      "/developer/applications/:id/details/change-app-name") => Forbidden() // app is not in production
      case Endpoint(_,      "/developer/applications/:id/details/change-privacy-policy-location") => Forbidden() // app is not in production
      case Endpoint(_,      "/developer/applications/:id/details/change-terms-conditions-location") => Forbidden() // app is not in production
      case Endpoint(_,      "/developer/applications/:id/details/change") => NotFound() // app is not approved
      case Endpoint(_,      "/developer/applications/:id/details/terms-of-use") => NotFound() // app is not approved
      case Endpoint("GET",  "/developer/applications/:id/details") => Redirect(s"/developer/submissions/application/${applicationId.value}/production-credentials-checklist")
      case Endpoint("GET",  "/developer/applications/:id/credentials") => NotFound() // app is not approved
      case Endpoint("GET",  "/developer/applications/:id/client-secrets") => BadRequest() // app is not approved
      case Endpoint("POST", "/developer/applications/:id/client-secret-new") => BadRequest() // app is not approved
      case Endpoint("GET",  "/developer/applications/:id/client-id") => BadRequest() // app is not approved
      case Endpoint(_,      "/developer/applications/:id/change-locked-subscription") => BadRequest()
      case Endpoint("GET",  "/developer/applications/:id/server-token") => BadRequest()
      case Endpoint(_,      "/developer/applications/:id/client-secret/:clientSecretId/delete") => BadRequest() // app is not approved
      case Endpoint("POST", "/developer/applications/:id/request-check") => BadRequest()
      case Endpoint("GET",  "/developer/applications/:id/add/subscription-configuration-step/:pageNumber") => Redirect(s"/developer/applications/${applicationId.value}/add/success")
      case Endpoint(_,      "/developer/submissions/application/:aid/start-using-your-application") => NotFound() // app is not in preprod state
      case Endpoint("GET",  "/developer/submissions/application/:aid/terms-of-use-responses") => NotFound() // app is not approved
      case Endpoint("GET",  "/developer/submissions/application/:aid/view-answers") => BadRequest() // app is not pending approval
      case Endpoint("GET",  "/developer/submissions/application/:aid/check-answers") => Redirect(s"/developer/submissions/application/${applicationId.value}/production-credentials-checklist")
      case Endpoint("GET",  "/developer/submissions/application/:aid/submit-request") => Redirect(s"/developer/submissions/application/${applicationId.value}/production-credentials-checklist")
      case Endpoint(_, path) if path.startsWith("/developer/applications/:id/redirect-uris") => NotFound() // app is not approved
      case Endpoint(_, path) if path.startsWith("/developer/applications/:id/delete") => NotFound() // app is not approved
      case Endpoint(_, path) if path.startsWith("/developer/applications/:id/ip-allowlist") => NotFound() // app is not approved
      case Endpoint(_, path) if path.startsWith("/developer/applications/:id/responsible-individual") => BadRequest() // app is not in production

      case _ => getEndpointSuccessResponse(endpoint)
    }
  }

}
