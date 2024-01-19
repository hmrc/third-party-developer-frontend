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

package uk.gov.hmrc.thirdpartydeveloperfrontend.domain

import scala.collection.immutable.ListSet

sealed trait ErrorCode

object ErrorCode {
  val values: ListSet[ErrorCode] = ListSet(LOCKED_ACCOUNT, BAD_REQUEST, INVALID_PASSWORD, PASSWORD_REQUIRED, USER_ALREADY_EXISTS)

  final case object LOCKED_ACCOUNT      extends ErrorCode
  final case object BAD_REQUEST         extends ErrorCode
  final case object INVALID_PASSWORD    extends ErrorCode
  final case object PASSWORD_REQUIRED   extends ErrorCode
  final case object USER_ALREADY_EXISTS extends ErrorCode

  def apply(text: String): Option[ErrorCode] = ErrorCode.values.find(_.toString() == text.toUpperCase)

  import play.api.libs.json.Format
  import uk.gov.hmrc.apiplatform.modules.common.domain.services.SealedTraitJsonFormatting
  implicit val format: Format[ErrorCode] = SealedTraitJsonFormatting.createFormatFor[ErrorCode]("Error Code", ErrorCode.apply)
}
