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

package uk.gov.hmrc.apiplatform.modules.common.services

import cats.data.{NonEmptyChain => NEC}

import play.api.libs.json._

trait NonEmptyChainFormatters {

  implicit def necReads[A](implicit r: Reads[A]): Reads[NEC[A]] =
    Reads
      .of[List[A]]
      .collect(
        JsonValidationError("expected a NonEmptyList but got an empty list")
      ) {
        case head :: tail => NEC.of(head, tail: _*)
      }

  implicit def necWrites[A](implicit w: Writes[A]): Writes[NEC[A]] =
    Writes
      .of[List[A]]
      .contramap(_.toNonEmptyList.toList)
}

object NonEmptyChainFormatters extends NonEmptyChainFormatters
