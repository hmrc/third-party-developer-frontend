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

package uk.gov.hmrc.thirdpartydeveloperfrontend.testdata

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.CollaboratorData
import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax

object CommonEmailData {
  val admin = CollaboratorData.Administrator.one.emailAddress

  val dev = CollaboratorData.Developer.one.emailAddress

  val altDev = "altdev@example.com".toLaxEmail

  val unverified = "iamunverified@example.com".toLaxEmail
}

trait CommonEmailFixtures {
  val adminEmail = CommonEmailData.admin

  val devEmail = CommonEmailData.dev

  val altDevEmail = CommonEmailData.altDev

  val unverifiedEmail = CommonEmailData.unverified
}
