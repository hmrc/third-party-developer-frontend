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

package uk.gov.hmrc.thirdpartydeveloperfrontend.service

import javax.inject.{Inject, Singleton}

import play.api.mvc.{AnyContent, Request}

import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.TermsOfUseVersion
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.services.TermsOfUseService
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.services.TermsOfUseService.TermsOfUseAgreementDetails
import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.ApplicationWithCollaborators

@Singleton
class TermsOfUseVersionService @Inject() (termsOfUseService: TermsOfUseService) {

  def getLatest()(implicit request: Request[AnyContent]): TermsOfUseVersion = {
    TermsOfUseVersion.latest
  }

  def getForApplication(application: ApplicationWithCollaborators)(implicit request: Request[AnyContent]): TermsOfUseVersion = {
    termsOfUseService.getAgreementDetails(application).lastOption
      .flatMap((tou: TermsOfUseAgreementDetails) => tou.version)
      .flatMap(TermsOfUseVersion.fromVersionString(_))
      .getOrElse(getLatest())
  }
}
