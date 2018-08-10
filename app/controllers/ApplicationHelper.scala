/*
 * Copyright 2018 HM Revenue & Customs
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

package controllers

import domain.Role.Role
import domain.{Application, ApplicationNotFound}
import jp.t2v.lab.play2.stackc.RequestWithAttributes
import service.ApplicationService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

trait ApplicationHelper {
  val applicationService: ApplicationService

  def fetchApp(id: String): Future[Application] =
    applicationService.fetchByApplicationId(id)(HeaderCarrier())

  def applicationForRequest(id: String)(implicit req: RequestWithAttributes[_], hc: HeaderCarrier): Future[Application] =
    req.get(AppKey).getOrElse(applicationService.fetchByApplicationId(id))

  def roleForApplication(application: Application, email: String): Role = application.role(email).getOrElse(throw new ApplicationNotFound)
}
