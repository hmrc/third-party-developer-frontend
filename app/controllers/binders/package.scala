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

package controllers

import domain.models.applications.Environment
import domain.models.controllers.{AddTeamMemberPageMode, SaveSubsFieldsPageMode}
import play.api.mvc.PathBindable
import domain.models.apidefinitions.ApiContext
import play.api.mvc.QueryStringBindable

package object binders {
  implicit def apiContextPathBinder(implicit textBinder: PathBindable[String]): PathBindable[ApiContext] = new PathBindable[ApiContext] {
    override def bind(key: String, value: String): Either[String, ApiContext] = {
      textBinder.bind(key, value).map(ApiContext(_))
    }

    override def unbind(key: String, apiContext: ApiContext): String = {
      apiContext.value
    }
  }

  implicit def apiContextQueryStringBindable(implicit textBinder: QueryStringBindable[String]) = new QueryStringBindable[ApiContext] {
    override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, ApiContext]] = {
      for {
        context <- textBinder.bind("context", params)
      } yield {
        context match {
          case Right(context) => Right(ApiContext(context))
          case _                        => Left("Unable to bind an api context")
        }
      }
    }
    override def unbind(key: String, context: ApiContext): String = {
      textBinder.unbind("context", context.value)
    }
  }

  
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

  implicit def saveSubsFieldsPageModePathBinder(implicit textBinder: PathBindable[String]): PathBindable[SaveSubsFieldsPageMode] =
    new PathBindable[SaveSubsFieldsPageMode] {
      override def bind(key: String, value: String): Either[String, SaveSubsFieldsPageMode] = {
        for {
          text <- textBinder.bind(key, value).right
          mode <- SaveSubsFieldsPageMode.from(text).toRight("Not a valid SaveSubsFieldsPageMode").right
        } yield mode
      }

      override def unbind(key: String, mode: SaveSubsFieldsPageMode): String = {
        mode.toString.toLowerCase
      }
    }
}