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

class NewJourneyAdminUserTestingStatusProductionEndpointScenarioSpec extends EndpointScenarioSpec
  with IsNewJourneyStandardApplication
  with UserIsAdmin
  with UserIsAuthenticated
  with AppDeployedToProductionEnvironment
  with AppHasTestingStatus {

  override def getExpectedResponse(endpoint: Endpoint): Response = {
    endpoint match {
      case Endpoint("GET",  "/developer/applications/:id/add/success") => NotFound()
      case Endpoint(_,      "/developer/applications/:id/change-locked-subscription") => BadRequest()
      case Endpoint("POST", "/developer/applications/:id/change-subscription") => getEndpointSuccessResponse(endpoint)
      case Endpoint("GET",  "/developer/applications/:id/client-id") => BadRequest()
      case Endpoint("GET",  "/developer/applications/:id/credentials") => NotFound()
      case Endpoint("GET",  "/developer/applications/:id/details") => Redirect(s"/developer/submissions/application/${applicationId.value}/production-credentials-checklist")
      case Endpoint(_,      "/developer/applications/:id/details/change") => Forbidden()
      case Endpoint(_,      "/developer/applications/:id/details/terms-of-use") => NotFound()
      case Endpoint("GET",  "/developer/applications/:id/request-check/submitted") => getEndpointSuccessResponse(endpoint)
      case Endpoint("GET",  "/developer/applications/:id/request-check/appDetails") => getEndpointSuccessResponse(endpoint)
      case Endpoint("GET",  "/developer/applications/:id/server-token") => BadRequest()
      case Endpoint("POST", "/developer/applications/:id/team-members/add/:addTeamMemberPageMode") => getEndpointSuccessResponse(endpoint)
      case Endpoint("POST", "/developer/registration") => BadRequest()
      case Endpoint("GET",  "/developer/registration") => Redirect(s"/developer/applications")
      case Endpoint("GET",  "/developer/reset-password/error") => BadRequest()
      case Endpoint("GET",  "/developer/submissions/application/:aid/check-answers") => Redirect(s"/developer/submissions/application/${applicationId.value}/production-credentials-checklist")
      case Endpoint(_,      "/developer/submissions/application/:aid/start-using-your-application") => NotFound()
      case Endpoint("GET",  "/developer/submissions/application/:aid/submit-request") => Redirect(s"/developer/submissions/application/${applicationId.value}/production-credentials-checklist")
      case Endpoint("GET",  "/developer/submissions/application/:aid/terms-of-use-responses") => NotFound()
      case Endpoint("GET",  "/developer/submissions/application/:aid/view-answers") => BadRequest()
      case Endpoint(_, path) if path.startsWith("/developer/applications/:id/check-your-answers") => BadRequest()
      case Endpoint(_, path) if path.startsWith("/developer/applications/:id/client-secret") => BadRequest()
      case Endpoint(_, path) if path.startsWith("/developer/applications/:id/delete") => NotFound()
      case Endpoint(_, path) if path.startsWith("/developer/applications/:id/details/change") => NotFound()
      case Endpoint(_, path) if path.startsWith("/developer/applications/:id/ip-allowlist") => NotFound()
      case Endpoint(_, path) if path.startsWith("/developer/applications/:id/redirect-uris") => NotFound()
      case Endpoint(_, path) if path.startsWith("/developer/applications/:id/request-check") => BadRequest()
      case Endpoint(_, path) if path.startsWith("/developer/applications/:id/responsible-individual") => NotFound()
      case Endpoint(_, path) if path.startsWith("/developer/applications/:id/team-members") => NotFound()

      case _ => getEndpointSuccessResponse(endpoint)
    }
  }

}
