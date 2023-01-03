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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.endpointauth

class OldJourneyDevUserProdStatusProductionEndpointScenarioSpec extends EndpointScenarioSpec
  with IsOldJourneyStandardApplication
  with UserIsDeveloper
  with UserIsAuthenticated
  with AppDeployedToProductionEnvironment
  with AppHasProductionStatus {

  override def getExpectedResponse(endpoint: Endpoint): Response = {
    endpoint match {
      case Endpoint("GET",  "/developer/applications") => Success()
      case Endpoint("GET",  "/developer/applications/:id/add/subscription-configuration-step/:pageNumber") => Redirect(s"/developer/applications/${applicationId.value}/request-check")
      case Endpoint("GET",  "/developer/applications/:id/request-check/submitted") => getEndpointSuccessResponse(endpoint)
      case Endpoint("GET",  "/developer/applications/:id/request-check/appDetails") => Success()
      case Endpoint("GET",  "/developer/registration") => Redirect("/developer/applications")
      case Endpoint("POST", "/developer/registration") => BadRequest()
      case Endpoint("GET",  "/developer/reset-password/error") => BadRequest()
      case Endpoint(_,      "/developer/submissions/application/:aid/production-credentials-checklist") => BadRequest() // must be in 'testing' state
      case Endpoint(_,      "/developer/submissions/application/:aid/cancel-request") => BadRequest() // must not be in production state
      case Endpoint("GET",  "/developer/submissions/application/:aid/check-answers") => BadRequest() // must be in testing state
      case Endpoint("GET",  "/developer/submissions/application/:aid/view-answers") => BadRequest() // must be in pending approval state
      case Endpoint("GET",  "/developer/submissions/application/:aid/submit-request") => BadRequest() // must be in testing state
      case Endpoint(_,      "/developer/submissions/application/:aid/start-using-your-application") => Forbidden()
      case Endpoint("GET",  "/developer/submissions/application/:aid/terms-of-use-responses") => Forbidden()
      case Endpoint("GET",  "/developer/applications/:id/client-secrets") => Forbidden()
      case Endpoint(_,      "/developer/applications/:id/change-private-subscription") => Forbidden()
      case Endpoint(_,      "/developer/applications/:id/change-locked-subscription") => Forbidden()
      case Endpoint("POST", "/developer/applications/:id/team-members/remove") => Forbidden()
      case Endpoint("POST", "/developer/applications/:id/team-members/add/:addTeamMemberPageMode") => Forbidden()
      case Endpoint("POST", "/developer/applications/:id/change-subscription") => BadRequest()
      case Endpoint(_,      "/developer/applications/:id/client-secret/:clientSecretId/delete") => Forbidden()
      case Endpoint("GET",  "/developer/applications/add/production") => BadRequest()
      case Endpoint(_,      "/developer/applications/:id/details/terms-of-use") => Forbidden()
      case Endpoint("GET",  "/developer/applications/:id/client-id") => Forbidden()
      case Endpoint("GET",  "/developer/applications/add/:id") => BadRequest()
      case Endpoint("GET",  "/developer/applications/:id/add/success") => NotFound()
      case Endpoint("POST", "/developer/applications/:id/client-secret-new") => Forbidden()
      case Endpoint("GET",  "/developer/applications/:id/server-token") => Forbidden()
      case Endpoint("GET",  "/developer/applications/:id/push-secrets") => Forbidden()
      case Endpoint("GET",  "/developer/applications/add/switch") => BadRequest()
      case Endpoint("GET",  "/developer/applications/:id/delete") => getEndpointSuccessResponse(endpoint)
      case Endpoint(_, path) if path.startsWith("/developer/applications/:id/details/change") => Forbidden()
      case Endpoint(_, path) if path.startsWith("/developer/applications/:id/responsible-individual/") => Forbidden()
      case Endpoint(_, path) if path.startsWith("/developer/applications/:id/check-your-answers") => BadRequest()
      case Endpoint(_, path) if path.startsWith("/developer/applications/:id/request-check") => BadRequest()
      case Endpoint(_, path) if path.startsWith("/developer/applications/:id/responsible-individual") => BadRequest()
      case Endpoint(_, path) if path.startsWith("/developer/applications/:id/ip-allowlist/") => Forbidden()
      case Endpoint(_, path) if path.startsWith("/developer/applications/:id/redirect-uris/") => Forbidden()
      case Endpoint(_, path) if path.startsWith("/developer/applications/:id/delete") => Forbidden()

      case _ => getEndpointSuccessResponse(endpoint)
    }
  }

}
