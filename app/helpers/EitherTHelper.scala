/*
 * Copyright 2021 HM Revenue & Customs
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

package helpers

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import cats.data.EitherT
import cats.instances.future.catsStdInstancesForFuture

trait EitherTHelper[E] {
  implicit val ec: ExecutionContext

  def fromOptionF[A](in: Future[Option[A]], error: => E): EitherT[Future,E,A] = EitherT.fromOptionF(in, error)
  def liftF[A](in: Future[A]): EitherT[Future,E,A]                            = EitherT.liftF(in)
  def fromOption[A](in: Option[A], error: => E): EitherT[Future,E,A]          = EitherT.fromOption(in, error)
  def fromEither[A](in: Either[E,A]): EitherT[Future,E,A]                     = EitherT.fromEither(in)
  def cond[A](in: => Boolean, right: => A, left: => E)                        = EitherT.cond[Future](in, right, left)
}
