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

package uk.gov.hmrc.thirdpartydeveloperfrontend.controllers

import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.{ApiContext, ApiVersion}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications.{ApplicationId, ClientId, Environment}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.{AddTeamMemberPageMode, SaveSubsFieldsPageMode}
import play.api.mvc.{PathBindable, QueryStringBindable}
import uk.gov.hmrc.apiplatform.modules.mfa.models.MfaId

import java.util.UUID

package object binders {
  implicit def clientIdPathBinder(implicit textBinder: PathBindable[String]): PathBindable[ClientId] = new PathBindable[ClientId] {
    override def bind(key: String, value: String): Either[String, ClientId] = {
      textBinder.bind(key, value).map(ClientId(_))
    }

    override def unbind(key: String, clientId: ClientId): String = {
      clientId.value
    }
  }

  implicit def applicationIdPathBinder(implicit textBinder: PathBindable[String]): PathBindable[ApplicationId] = new PathBindable[ApplicationId] {
    override def bind(key: String, value: String): Either[String, ApplicationId] = {
      textBinder.bind(key, value).map(ApplicationId(_))
    }

    override def unbind(key: String, applicationId: ApplicationId): String = {
      applicationId.value
    }
  }

  implicit def mfaIdPathBinder(implicit textBinder: PathBindable[String]): PathBindable[MfaId] = new PathBindable[MfaId] {
    override def bind(key: String, value: String): Either[String, MfaId] = {
      textBinder.bind(key, value).map(x => MfaId(UUID.fromString(x)))
    }

    override def unbind(key: String, mfaId: MfaId): String = {
      mfaId.value.toString
    }
  }

  implicit def applicationIdQueryStringBindable(implicit textBinder: QueryStringBindable[String]) = new QueryStringBindable[ApplicationId] {
    override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, ApplicationId]] = {
      for {
        context <- textBinder.bind("context", params)
      } yield {
        context match {
          case Right(context) => Right(ApplicationId(context))
          case _              => Left("Unable to bind an api context")
        }
      }
    }
    override def unbind(key: String, context: ApplicationId): String = {
      textBinder.unbind("context", context.value)
    }
  }

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
          case _              => Left("Unable to bind an api context")
        }
      }
    }
    override def unbind(key: String, context: ApiContext): String = {
      textBinder.unbind("context", context.value)
    }
  }

  implicit def apiVersionPathBinder(implicit textBinder: PathBindable[String]): PathBindable[ApiVersion] = new PathBindable[ApiVersion] {
    override def bind(key: String, value: String): Either[String, ApiVersion] = {
      textBinder.bind(key, value).map(ApiVersion(_))
    }

    override def unbind(key: String, apiVersion: ApiVersion): String = {
      apiVersion.value
    }
  }

  implicit def apiVersionQueryStringBindable(implicit textBinder: QueryStringBindable[String]) = new QueryStringBindable[ApiVersion] {
    override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, ApiVersion]] = {
      for {
        version <- textBinder.bind("version", params)
      } yield {
        version match {
          case Right(version) => Right(ApiVersion(version))
          case _              => Left("Unable to bind an api version")
        }
      }
    }
    override def unbind(key: String, version: ApiVersion): String = {
      textBinder.unbind("version", version.value)
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
