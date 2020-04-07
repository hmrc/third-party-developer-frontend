/*
 * Copyright 2020 HM Revenue & Customs
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

package domain

import enumeratum.{EnumEntry, PlayEnum}

sealed trait SubscriptionRedirect extends EnumEntry

object SubscriptionRedirect extends PlayEnum[SubscriptionRedirect] {
  val values = findValues

  final case object MANAGE_PAGE extends SubscriptionRedirect
  final case object APPLICATION_CHECK_PAGE extends SubscriptionRedirect
  final case object API_SUBSCRIPTIONS_PAGE extends SubscriptionRedirect
  final case object API_MANAGE_METADATA_PAGE extends SubscriptionRedirect
}
