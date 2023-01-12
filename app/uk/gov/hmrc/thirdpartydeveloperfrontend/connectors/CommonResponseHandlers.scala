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

package uk.gov.hmrc.thirdpartydeveloperfrontend.connectors

import uk.gov.hmrc.http.UpstreamErrorResponse
import play.api.http.Status._

trait CommonResponseHandlers {

  type ErrorOr[A] = Either[UpstreamErrorResponse, A]

  type ErrorOrUnit = Either[UpstreamErrorResponse, Unit]

  val throwOrUnit = throwOr(()) _

  def throwOr[A](successValue: A)(either: ErrorOrUnit): A =
    either match {
      case Left(err) => throw err
      case Right(_)  => successValue
    }

  def throwOrOptionOf[A](either: ErrorOr[A]): Option[A] =
    either match {
      case Right(a)                                        => Some(a)
      case Left(UpstreamErrorResponse(_, NOT_FOUND, _, _)) => None
      case Left(err)                                       => throw err
    }
}
