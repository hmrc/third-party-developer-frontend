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
import uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.endpointauth.preconditions.{DeskproTicketCreationSucceeds, NoUserIdFoundForEmailAddressValue, PasswordResetSucceeds, UserRegistrationSucceeds, UserVerificationSucceeds}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.{ApplicationId, Environment}

class NoAuthEndpointScenarioSpec extends EndpointScenarioSpec
    with NoUserIdFoundForEmailAddressValue
    with DeskproTicketCreationSucceeds
    with UserVerificationSucceeds
    with PasswordResetSucceeds
    with UserRegistrationSucceeds {

  override def describeScenario(): String = "User is not authenticated"

  override def getGlobalPathParameterValues(): Map[String,String] = Map(
    "id" -> ApplicationId.random.value,
    "environment" -> Environment.PRODUCTION.entryName,
    "pageNumber" -> "1",
    "context" -> "ctx",
    "version" -> "1.0",
    "saveSubsFieldsPageMode"-> "lefthandnavigation",
    "fieldName"-> "field1",
    "addTeamMemberPageMode" -> "applicationcheck",
    "file" -> "javascripts/loader.js"
  )

  override def getEndpointSpecificQueryParameterValues(endpoint: Endpoint): Map[String,String] = {
    endpoint match {
      case Endpoint("GET", "/applications/:id/change-locked-subscription") => Map("name" -> "my-api", "context" -> "ctx", "version" -> "1.0", "redirectTo" -> "http://example.com")
      case Endpoint("POST", "/applications/:id/change-locked-subscription") => Map("name" -> "my-api", "context" -> "ctx", "version" -> "1.0", "redirectTo" -> "http://example.com")
      case Endpoint("GET", "/applications/:id/change-private-subscription") => Map("name" -> "my-api", "context" -> "ctx", "version" -> "1.0", "redirectTo" -> "http://example.com")
      case Endpoint("POST", "/applications/:id/change-private-subscription") => Map("name" -> "my-api", "context" -> "ctx", "version" -> "1.0", "redirectTo" -> "http://example.com")
      case Endpoint("POST", "/applications/:id/change-subscription") => Map("context" -> "ctx", "version" -> "1.0", "redirectTo" -> "http://example.com")
      case Endpoint("GET", "/applications/:id/ip-allowlist/remove") => Map("cidrBlock" -> "192.168.1.2/8")
      case Endpoint("POST", "/applications/:id/ip-allowlist/remove") => Map("cidrBlock" -> "192.168.1.2/8")
      case Endpoint("GET", "/verification") => Map("code" -> "CODE123")
      case Endpoint("GET", "/reset-password-link") => Map("code" -> "1324")
      case Endpoint("GET", "/application-verification") => Map("code" -> "1324")

      case _ => Map.empty
    }
  }

  override def getEndpointSpecificBodyParameterValues(endpoint: Endpoint): Option[Map[String,String]] = {
    Option(endpoint match {
      case Endpoint("POST", "/registration") => Map("firstname" -> "Bob", "lastname" -> "Example", "emailaddress" -> "bob@example.com", "password" -> "S3curE-Pa$$w0rd!", "confirmpassword" -> "S3curE-Pa$$w0rd!")
      case Endpoint("POST", "/login") => Map("emailaddress" -> "bob@example.com", "password" -> "letmein")
      case Endpoint("POST", "/forgot-password") => Map("emailaddress" -> "user@example.com")
      case Endpoint("POST", "/login-totp") => Map("accessCode" -> "123456", "rememberMe" -> "false")
      case Endpoint("POST", "/reset-password") => Map("password" -> "S3curE-Pa$$w0rd!", "confirmpassword" -> "S3curE-Pa$$w0rd!")
      case Endpoint("POST", "/support") => Map("fullname" -> "Bob Example", "emailaddress" -> "bob@example.com", "comments" -> "I am very cross about something")
      case _ => null
    })
  }

  override def expectedResponses(): ExpectedResponses = ExpectedResponses(
    Redirect("/developer/login"),
    ExpectedResponseOverride(Endpoint("GET", "/login"), Success()),
    ExpectedResponseOverride(Endpoint("POST", "/login"), Unauthorized()),
    ExpectedResponseOverride(Endpoint("GET", "/registration"), Success()),
    ExpectedResponseOverride(Endpoint("POST", "/registration"), Redirect("/developer/confirmation")),
    ExpectedResponseOverride(Endpoint("GET", "/login-totp"), Success()),
    //TODO the request below triggers a deskpro ticket to be created (should unauth users be able to do this?) that requests 2SV removal for any account, seems wrong? do SDST verify that the correct user made the request?
    ExpectedResponseOverride(Endpoint("GET", "/login/2SV-help"), Success()),
    ExpectedResponseOverride(Endpoint("POST", "/login/2SV-help"), Success()),
    ExpectedResponseOverride(Endpoint("GET", "/login/2SV-help/complete"), Success()),
    ExpectedResponseOverride(Endpoint("GET", "/confirmation"), Success()),
    ExpectedResponseOverride(Endpoint("GET", "/resend-confirmation"), Success()),
    ExpectedResponseOverride(Endpoint("POST", "/confirmation"), Success()),
    ExpectedResponseOverride(Endpoint("GET", "/support/submitted"), Success()),
    ExpectedResponseOverride(Endpoint("GET", "/verification"), Success()),
    ExpectedResponseOverride(Endpoint("GET", "/resend-verification"), BadRequest()),
    ExpectedResponseOverride(Endpoint("GET", "/locked"), Locked()),
    ExpectedResponseOverride(Endpoint("GET", "/reset-password"), Redirect("/developer/reset-password/error")),
    ExpectedResponseOverride(Endpoint("GET", "/forgot-password"), Success()),
    ExpectedResponseOverride(Endpoint("POST", "/forgot-password"), Success()),
    ExpectedResponseOverride(Endpoint("GET", "/user-navlinks"), Success()),
    ExpectedResponseOverride(Endpoint("GET", "/logout"), Success()),
    ExpectedResponseOverride(Endpoint("GET", "/partials/terms-of-use"), Success()),
    ExpectedResponseOverride(Endpoint("GET", "/reset-password-link"), Redirect("/developer/reset-password")),
    ExpectedResponseOverride(Endpoint("GET", "/reset-password/error"), BadRequest()),
    ExpectedResponseOverride(Endpoint("GET", "/support"), Success()),
    ExpectedResponseOverride(Endpoint("POST", "/support"), Redirect("/developer/support/submitted")),
    ExpectedResponseOverride(Endpoint("GET", "/assets/*file"), Success()),
    ExpectedResponseOverride(Endpoint("POST", "/reset-password"), Error("java.lang.RuntimeException: email not found in session")),
    ExpectedResponseOverride(Endpoint("POST", "/login-totp"), Error("java.util.NoSuchElementException: None.get"))
  )

}
