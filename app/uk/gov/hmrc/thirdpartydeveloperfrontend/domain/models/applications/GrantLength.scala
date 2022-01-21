/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications

import java.time.Period

object GrantLength {
  val MONTH: Period = Period.ofDays(30)
  val THREE_MONTHS: Period = Period.ofDays(90)
  val SIX_MONTHS: Period = Period.ofDays(180)
  val ONE_YEAR: Period = Period.ofDays(365)
  val EIGHTEEN_MONTHS: Period = Period.ofDays(547)
  val THREE_YEARS: Period = Period.ofDays(1095)
  val FIVE_YEARS: Period = Period.ofDays(1825)
  val TEN_YEARS: Period = Period.ofDays(3650)
  val HUNDRED_YEARS: Period = Period.ofDays(36500)
}
