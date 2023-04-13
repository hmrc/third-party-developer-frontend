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

package uk.gov.hmrc.apiplatform.modules.commands.applications.domain.models

import scala.concurrent.Future
import scala.concurrent.ExecutionContext

trait Types extends CommandHandlerTypes[DispatchSuccessResult]

object Types extends Types {
  import cats.syntax.all._

  implicit class SuccessSyntax(successValue: Success) {
    def asSuccess(implicit ec: ExecutionContext): Result = successValue.asRight[Failures].pure[Future]
  }

  implicit class FailureSyntax(failureValue: CommandFailure) {
    def asFailure(implicit ec: ExecutionContext): Result = failureValue.leftNec[Success].pure[Future]
  }
  
  implicit class FailuresSyntax(failureValues: Failures) {
    def asFailure(implicit ec: ExecutionContext): Result = failureValues.asLeft[Success].pure[Future]
  }
}
