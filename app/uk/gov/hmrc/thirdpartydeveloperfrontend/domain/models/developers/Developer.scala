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

package uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.developers

import play.api.libs.functional.syntax._
import play.api.libs.json.{Format, JsPath, Json, Reads}

import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress
import uk.gov.hmrc.apiplatform.modules.developers.domain.models.UserId
import uk.gov.hmrc.apiplatform.modules.mfa.models.MfaDetail
import uk.gov.hmrc.apiplatform.modules.mfa.models.MfaDetailFormats._
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.emailpreferences.EmailPreferences

case class Developer(
    userId: UserId,
    email: LaxEmailAddress,
    firstName: String,
    lastName: String,
    organisation: Option[String] = None,
    mfaDetails: List[MfaDetail] = List.empty,
    emailPreferences: EmailPreferences = EmailPreferences.noPreferences
  )

object Developer {

  val developerReads: Reads[Developer] = (
    (JsPath \ "userId").read[UserId] and
      (JsPath \ "email").read[LaxEmailAddress] and
      (JsPath \ "firstName").read[String] and
      (JsPath \ "lastName").read[String] and
      (JsPath \ "organisation").readNullable[String] and
      ((JsPath \ "mfaDetails").read[List[MfaDetail]] or Reads.pure(List.empty[MfaDetail])) and
      ((JsPath \ "emailPreferences").read[EmailPreferences] or Reads.pure(EmailPreferences.noPreferences))
  )(Developer.apply _)

  val developerWrites          = Json.writes[Developer]
  implicit val formatDeveloper = Format(developerReads, developerWrites)
}
