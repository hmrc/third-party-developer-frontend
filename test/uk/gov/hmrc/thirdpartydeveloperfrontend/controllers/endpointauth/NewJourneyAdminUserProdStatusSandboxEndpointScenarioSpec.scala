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

class NewJourneyAdminUserProdStatusSandboxEndpointScenarioSpec extends EndpointScenarioSpec
    with IsNewJourneyStandardApplicationWithoutSubmission
    with UserIsAdmin
    with UserIsAuthenticated
    with AppDeployedToSandboxEnvironment
    with AppHasProductionStatus {

  override def getExpectedResponse(endpoint: Endpoint): Response = {
    endpoint match {
      case Endpoint("GET", "/developer/applications/:id/add/subscription-configuration-step/:pageNumber", _) =>
        Redirect(s"/developer/applications/${applicationId}/add/success")
      case Endpoint(_, "/developer/applications/:id/change-locked-subscription", _)                          => BadRequest()
      case Endpoint(_, "/developer/applications/:id/details/change", _)                                      => getEndpointSuccessResponse(endpoint)
      case Endpoint(_, "/developer/applications/:id/details/terms-of-use", _)                                => BadRequest()
      case Endpoint("GET", "/developer/applications/:id/agree-new-terms-of-use", _)                          =>
        Redirect(s"/developer/submissions/application/${applicationId}/view-answers")
      case Endpoint("GET", "/developer/applications/:id/request-check/appDetails", _)                        => getEndpointSuccessResponse(endpoint)
      case Endpoint("GET", "/developer/applications/:id/request-check/submitted", _)                         => getEndpointSuccessResponse(endpoint)
      case Endpoint("POST", "/developer/registration", _)                                                    => BadRequest()
      case Endpoint("GET", "/developer/registration", _)                                                     => Redirect(s"/developer/applications")
      case Endpoint("GET", "/developer/reset-password/error", _)                                             => BadRequest()
      case Endpoint(_, "/developer/submissions/application/:aid/cancel-request", _)                          => BadRequest()
      case Endpoint("GET", "/developer/submissions/application/:aid/check-answers", _)                       =>
        Redirect(s"/developer/submissions/application/${applicationId}/production-credentials-checklist")
      case Endpoint(_, "/developer/submissions/application/:aid/production-credentials-checklist", _)        => Success() // can be in 'production' state for new terms of use
      case Endpoint(_, "/developer/submissions/application/:aid/start-using-your-application", _)            => NotFound()
      case Endpoint("GET", "/developer/submissions/application/:aid/submit-request", _)                      =>
        Redirect(s"/developer/submissions/application/${applicationId}/production-credentials-checklist")
      case Endpoint(_, path, _) if path.startsWith("/developer/applications/:id/check-your-answers")         => BadRequest()
      case Endpoint(_, path, _) if path.startsWith("/developer/applications/:id/details/change")             => Forbidden()
      case Endpoint(_, path, _) if path.startsWith("/developer/applications/:id/request-check")              => BadRequest()
      case Endpoint(_, path, _) if path.startsWith("/developer/applications/:id/responsible-individual")     => BadRequest()

      case _ => getEndpointSuccessResponse(endpoint)
    }
  }

}
