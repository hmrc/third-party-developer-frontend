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

class UnauthenticatedUserEndpointScenarioSpec extends EndpointScenarioSpec
    with IsNewJourneyStandardApplication
    with UserIsAdmin
    with AppDeployedToProductionEnvironment
    with AppHasProductionStatus
    with UserIsNotAuthenticated {

  override def getExpectedResponse(endpoint: Endpoint): Response = {
    endpoint match {
      case Endpoint("GET", "/developer/login", _)                                               => Success()
      case Endpoint("POST", "/developer/login", _)                                              => Unauthorized()
      case Endpoint(_, "/developer/registration", _)                                            => getEndpointSuccessResponse(endpoint)
      case Endpoint("GET", "/developer/login/select-mfa/try-another-option", _)                 => Error("java.util.NoSuchElementException: None.get")
      case Endpoint("GET", "/developer/login-mfa", _)                                           => Error("java.util.NoSuchElementException: None.get")
      case Endpoint("POST", "/developer/login-mfa", _)                                          => Error("java.util.NoSuchElementException: None.get")
      case Endpoint("GET", "/developer/login/select-mfa", _)                                    => Success()
      case Endpoint("POST", "/developer/login/select-mfa", _)                                   => Error("java.util.NoSuchElementException: None.get")
      case Endpoint("GET", "/developer/application-verification", _)                            => Success()
      case Endpoint(_, "/developer/login/2SV-help", _)                                          => Success()
      case Endpoint("GET", "/developer/login/2SV-help/complete", _)                             => Success()
      case Endpoint(_, "/developer/confirmation", _)                                            => Success()
      case Endpoint("GET", "/developer/resend-confirmation", _)                                 => Success()
      case Endpoint("GET", "/developer/support/submitted", _)                                   => Success()
      case Endpoint("GET", "/developer/verification", _)                                        => Success()
      case Endpoint("GET", "/developer/resend-verification", _)                                 => BadRequest()
      case Endpoint("GET", "/developer/locked", _)                                              => Locked()
      case Endpoint("GET", "/developer/reset-password", _)                                      => Redirect("/developer/reset-password/error")
      case Endpoint("POST", "/developer/reset-password", _)                                     => Error("java.lang.RuntimeException: email not found in session")
      case Endpoint("GET", "/developer/reset-password-link", _)                                 => Redirect("/developer/reset-password")
      case Endpoint("GET", "/developer/reset-password/error", _)                                => BadRequest()
      case Endpoint(_, "/developer/forgot-password", _)                                         => Success()
      case Endpoint("GET", "/developer/user-navlinks", _)                                       => Success()
      case Endpoint("GET", "/developer/logout", _)                                              => Success()
      case Endpoint("GET", "/developer/partials/terms-of-use", _)                               => Success()
      case Endpoint(_, "/developer/support", _)                                                 => getEndpointSuccessResponse(endpoint)
      case Endpoint("GET", "/developer/assets/*file", _)                                        => Success()
      case Endpoint(_, "/developer/submissions/responsible-individual-verification", _)         => Success()
      case Endpoint("GET", "/developer/new-support", _)                                         => Success()
      case Endpoint("POST", "/developer/new-support", _)                                        => Redirect("/developer/new-support/api/choose-api")
      case Endpoint("GET", "/developer/new-support/api/choose-api", _)                          => Success()
      case Endpoint("POST", "/developer/new-support/api/choose-api", _)                         => Redirect("/developer/new-support/details")
      case Endpoint("GET", "/developer/new-support/api/private-api", _)                         => Redirect("/developer/new-support/api/choose-api")
      case Endpoint("POST", "/developer/new-support/api/private-api", _)                        => Redirect("/developer/new-support/api/choose-api")
      case Endpoint("GET", "/developer/new-support/api/private-api/apply", _)                   => Redirect("/developer/new-support/api/private-api")
      case Endpoint("POST", "/developer/new-support/api/private-api/apply", _)                  => Redirect("/developer/new-support/api/private-api")
      case Endpoint("GET", "/developer/new-support/api/private-api/cds-check", _)               => Redirect("/developer/new-support/api/private-api")
      case Endpoint("POST", "/developer/new-support/api/private-api/cds-check", _)              => Redirect("/developer/new-support/api/private-api")
      case Endpoint("GET", "/developer/new-support/api/private-api/cds-access-not-required", _) => Success()
      case Endpoint("GET", "/developer/new-support/signing-in", _)                              => Redirect("/developer/new-support")
      case Endpoint("POST", "/developer/new-support/signing-in", _)                             => Redirect("/developer/new-support")
      case Endpoint("GET", "/developer/new-support/signing-in/remove-access-codes", _)          => Success()
      case Endpoint("GET", "/developer/new-support/app", _)                                     => Redirect("/developer/new-support")
      case Endpoint("POST", "/developer/new-support/app", _)                                    => Redirect("/developer/new-support")
      case Endpoint("GET", "/developer/new-support/app/giving-team-member-access", _)           => Success()
      case Endpoint("GET", "/developer/new-support/details", _)                                 => Success()
      case Endpoint("POST", "/developer/new-support/details", _)                                => Redirect("/developer/new-support/confirmation")
      case Endpoint("GET", "/developer/new-support/confirmation", _)                            => Redirect("/developer/support?useNewSupport=true")
      case _                                                                                    => Redirect("/developer/login")
    }
  }
}
