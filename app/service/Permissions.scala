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

import domain.AccessType.{PRIVILEGED, ROPC, STANDARD}
import domain.Environment.{PRODUCTION, SANDBOX}
import domain.Role.ADMINISTRATOR
import domain.{Application, Developer}

class Permissions(user: Developer, application: Application) {

  private val isAdmin = application.role(user.email).contains(ADMINISTRATOR)

  val viewCredentialsLandingPage: Boolean = true

  val editCredentials: Boolean = (application.deployedTo, isAdmin) match {
    case (PRODUCTION, true) => true
    case (SANDBOX, true) => true
  }

  val viewSubscriptions: Boolean = application.access.accessType match {
    case STANDARD => true
    case _ => false
  }

  val editSubscriptions: Boolean =  (application.access.accessType, application.deployedTo, isAdmin) match {
    case (STANDARD, PRODUCTION, true) => true
    case (STANDARD, SANDBOX, _) => true
    case (_, _, _) => false
  }
}