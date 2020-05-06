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

package connectors

import controllers.ManageSubscriptions.ApiSubscriptionEditPageMode
import domain.{AddTeamMemberPageMode, Environment}
import play.api.mvc.PathBindable

package object binders {
  implicit def environmentPathBinder(implicit textBinder: PathBindable[String]): PathBindable[Environment] = new PathBindable[Environment] {
    override def bind(key: String, value: String): Either[String, Environment] = {
      for {
        text <- textBinder.bind(key, value).right
        env <- Environment.from(text).toRight("Not a valid environment").right
      } yield env
    }

    override def unbind(key: String, env: Environment): String = {
      env.toString.toLowerCase
    }
  }

  implicit def addTeamMemberPageModePathBinder(implicit textBinder: PathBindable[String]): PathBindable[AddTeamMemberPageMode] =
    new PathBindable[AddTeamMemberPageMode] {
      override def bind(key: String, value: String): Either[String, AddTeamMemberPageMode] = {
        for {
          text <- textBinder.bind(key, value).right
          mode <- AddTeamMemberPageMode.from(text).toRight("Not a valid AddTeamMemberPageMode").right
        } yield mode
      }

      override def unbind(key: String, mode: AddTeamMemberPageMode): String = {
        mode.toString.toLowerCase
      }
    }

  //connectors.ManageSubscriptions.ApiSubscriptionEditPageMode

  implicit def apiSubscriptionEditPageModePathBinder(implicit textBinder: PathBindable[String]): PathBindable[ApiSubscriptionEditPageMode] =
    new PathBindable[ApiSubscriptionEditPageMode] {
      override def bind(key: String, value: String): Either[String, ApiSubscriptionEditPageMode] = {
        for {
          text <- textBinder.bind(key, value).right
          mode <- ApiSubscriptionEditPageMode.from(text).toRight("Not a valid ApiSubscriptionEditPageMode").right
        } yield mode
      }

      override def unbind(key: String, mode: ApiSubscriptionEditPageMode): String = {
        mode.toString.toLowerCase
      }
    }
}
