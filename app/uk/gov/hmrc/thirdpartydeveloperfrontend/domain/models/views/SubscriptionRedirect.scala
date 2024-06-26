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

package uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.views

sealed trait SubscriptionRedirect

object SubscriptionRedirect {
  final case object MANAGE_PAGE            extends SubscriptionRedirect
  final case object API_SUBSCRIPTIONS_PAGE extends SubscriptionRedirect

  val values = List(MANAGE_PAGE, API_SUBSCRIPTIONS_PAGE)

  def apply(text: String): Option[SubscriptionRedirect] = SubscriptionRedirect.values.find(_.toString() == text.toUpperCase)
}
