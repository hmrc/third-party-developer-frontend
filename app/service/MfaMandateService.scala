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

package service

import config.ApplicationConfig
import javax.inject.{Inject, Singleton}
import org.joda.time.{Days, LocalDate}
import uk.gov.hmrc.http.HeaderCarrier
import domain.models.developers.UserId

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MfaMandateService @Inject()(val appConfig: ApplicationConfig, val applicationService: ApplicationService)(implicit val ec: ExecutionContext) {

  def showAdminMfaMandatedMessage(userId: UserId)(implicit hc: HeaderCarrier): Future[Boolean] = {
    mfaMandateCheck(userId, mandatedDate => mandatedDate.isAfter(new LocalDate()))
  }

  def isMfaMandatedForUser(userId: UserId)(implicit hc: HeaderCarrier): Future[Boolean] = {
    mfaMandateCheck(userId, mandatedDate => !(mandatedDate.isAfter(new LocalDate())))
  }

  private def mfaMandateCheck(userId: UserId, dateCheck : LocalDate => Boolean)(implicit hc: HeaderCarrier): Future[Boolean] = {
    isAdminOnProductionApplication(userId).map(isAdminOnProductionApplication =>
      if (isAdminOnProductionApplication) {
        appConfig.dateOfAdminMfaMandate.fold(false)((mandatedDate: LocalDate) => dateCheck(mandatedDate))
      } else false
    )
  }

  private def isAdminOnProductionApplication(userId: UserId)(implicit hc: HeaderCarrier): Future[Boolean] = {
    applicationService.fetchByTeamMemberUserId(userId).map(applications => {
      applications
        .filter(app => app.deployedTo.isProduction())
        .flatMap(app => app.collaborators)
        .filter(collaborators => collaborators.userId == userId)
        .exists(collaborator => collaborator.role.isAdministrator)
    }
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
  def parseLocalDate(value: String): Option[LocalDate] = {
    value.trim match {
      case "" => None
      case _ => Some(LocalDate.parse(value))
    }
  }
}


