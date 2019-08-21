/*
 * Copyright 2019 HM Revenue & Customs
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

package service

import config.ApplicationConfig
import org.joda.time.{Days, LocalDate}
import javax.inject.{Inject, Singleton}

@Singleton
class MfaMandateService @Inject()(val appConfig: ApplicationConfig) {
  def showAdminMfaMandatedMessage: Boolean = {
    appConfig.dateOfAdminMfaMandate.fold(false)((mandatedDate: LocalDate) => mandatedDate.isAfter(new LocalDate()))
  }

  def daysTillAdminMfaMandate: Option[Int] = {

    def mandatedDateToDays(mandatedDate: LocalDate) = {
      Days.daysBetween(new LocalDate, mandatedDate).getDays
    } match {
      case days if days < 0 => None
      case days => Some(days)
    }

    appConfig.dateOfAdminMfaMandate.fold[Option[Int]](None)(mandatedDateToDays)
  }
}

object MfaMandateService {
  def parseLocalDate(value: Option[String]): Option[LocalDate] = {
    value.flatMap((configValue: String) => {
      configValue.trim match {
        case "" => None
        case _ => Some(LocalDate.parse(configValue))
      }
    })
  }
}


