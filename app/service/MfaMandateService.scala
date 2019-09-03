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
import domain.{Application, Environment, Role}
import javax.inject.{Inject, Singleton}
import org.joda.time.{Days, LocalDate}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MfaMandateService @Inject()(val appConfig: ApplicationConfig, val applicationService: ApplicationService)(implicit val ec: ExecutionContext) {

  def showAdminMfaMandatedMessage(email: String)(implicit hc: HeaderCarrier): Future[Boolean] = {
    mfaMandateCheck(email, mandatedDate => mandatedDate.isAfter(new LocalDate()))
  }

  def isMfaMandatedForUser(email: String)(implicit hc: HeaderCarrier): Future[Boolean] = {
    mfaMandateCheck(email, mandatedDate => mandatedDate.isBefore(new LocalDate()))
  }

  private def mfaMandateCheck(email: String, dateCheck : LocalDate => Boolean)(implicit hc: HeaderCarrier): Future[Boolean] = {
    isAdminOnProductionApplication(email).map(isAdminOnProductionApplication =>
      if (isAdminOnProductionApplication) {
        appConfig.dateOfAdminMfaMandate.fold(false)((mandatedDate: LocalDate) => dateCheck(mandatedDate))
      } else false
    )
  }

  private def isAdminOnProductionApplication(email: String)(implicit hc: HeaderCarrier): Future[Boolean] = {
    applicationService.fetchByTeamMemberEmail(email).map(applications =>
      applications
        .filter(app => app.deployedTo == Environment.PRODUCTION)
        .flatMap(app => app.collaborators)
        .filter(collaborators => collaborators.emailAddress == email)
        .exists(collaborator => collaborator.role == Role.ADMINISTRATOR)
    )
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


