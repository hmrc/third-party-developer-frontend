/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.thirdpartydeveloperfrontend.helpers

import java.time.{Instant, LocalDate, LocalDateTime, ZoneOffset}

// TODO APIS-6715 Move to api-platform-common-domain library?
object InstantConversion {

  implicit class LocalDateTimeSyntax(localDateTime: LocalDateTime) {
    def asInstant: Instant = localDateTime.toInstant(ZoneOffset.UTC)
  }

  implicit class LocalDateSyntax(localDate: LocalDate) {
    def asInstant: Instant = localDate.atTime(0, 0).toInstant(ZoneOffset.UTC)
  }

  implicit class InstantSyntax(instant: Instant) {
    def asLocalDate: LocalDate = LocalDate.ofInstant(instant, ZoneOffset.UTC)
  }

}
