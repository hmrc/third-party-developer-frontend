/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.support

object SupportData {

  sealed trait PrimaryChoice {
    def id: String
    def text: String
  }

  case object FindingAnApi extends PrimaryChoice {
    val id   = "finding-an-api"
    val text = "Finding the API needed to build my software"
  }

  case object UsingAnApi extends PrimaryChoice {
    val id   = "using-an-api"
    val text = "Using an API"
  }

  case object SigningIn extends PrimaryChoice {
    val id   = "signing-into-account"
    val text = "Signing in to my account"
  }

  case object SettingUpApplication extends PrimaryChoice {
    val id   = "setting-up-application"
    val text = "Setting up or managing an application"
  }

  sealed trait ApiSecondaryChoice {
    def id: String
    def text: String
  }

  case object MakingAnApiCall extends ApiSecondaryChoice {
    val id   = "making-an-api-call"
    val text = "Making an API call"
  }

  case object GettingExamples extends ApiSecondaryChoice {
    val id   = "getting-examples"
    val text = "Getting examples of payloads or schemas"
  }

  case object ReportingDocumentation extends ApiSecondaryChoice {
    val id   = "reporting-documentation"
    val text = "Reporting documentation for an API that is inaccurate or missing information"
  }

  case object PrivateApiDocumentation extends ApiSecondaryChoice {
    val id   = "private-api-documentation"
    val text = "Getting access to documentation for a private API"
  }

  case object ForgottenPassword extends ApiSecondaryChoice {
    val id   = "forgotten-password"
    val text = "I've forgotten my password"
  }

  case object AccessCodes extends ApiSecondaryChoice {
    val id   = "access-codes"
    val text = "I can't get access codes to login"
  }

  case object ChooseBusinessRates {
    val id   = "business-rates"
    val text = "Business Rates 2.0"
  }

  case object ChooseCDS {
    val id   = "customs-declarations"
    val text = "Customs Declarations"
  }
}
