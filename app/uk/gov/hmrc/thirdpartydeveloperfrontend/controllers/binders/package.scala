/*
 * Copyright 2023 HM Revenue & Customs
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

import java.util.UUID
import java.{util => ju}
import scala.util.Try

import play.api.mvc.{PathBindable, QueryStringBindable}

import uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.ClientSecret
import uk.gov.hmrc.apiplatform.modules.common.domain.models._
import uk.gov.hmrc.apiplatform.modules.mfa.models.{MfaAction, MfaId, MfaType}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers.{AddTeamMemberPageMode, SaveSubsFieldsPageMode}

package object binders {

  implicit def clientIdPathBinder(implicit textBinder: PathBindable[String]): PathBindable[ClientId] = new PathBindable[ClientId] {

    override def bind(key: String, value: String): Either[String, ClientId] = {
      textBinder.bind(key, value).map(ClientId(_))
    }

    override def unbind(key: String, clientId: ClientId): String = {
      clientId.value
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

  implicit def apiContextPathBinder(implicit textBinder: PathBindable[String]): PathBindable[ApiContext] = new PathBindable[ApiContext] {

    override def bind(key: String, value: String): Either[String, ApiContext] = {
      textBinder.bind(key, value).map(ApiContext(_))
    }

    override def unbind(key: String, apiContext: ApiContext): String = {
      apiContext.value
    }
  }

  implicit def apiContextQueryStringBindable(implicit textBinder: QueryStringBindable[String]): QueryStringBindable[ApiContext] = new QueryStringBindable[ApiContext] {

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

  implicit def apiVersionNbrPathBinder(implicit textBinder: PathBindable[String]): PathBindable[ApiVersionNbr] = new PathBindable[ApiVersionNbr] {

    override def bind(key: String, value: String): Either[String, ApiVersionNbr] = {
      textBinder.bind(key, value).map(ApiVersionNbr(_))
    }

    override def unbind(key: String, apiVersion: ApiVersionNbr): String = {
      apiVersion.value
    }
  }

  private def clientSecretIdFromString(text: String): Either[String, ClientSecret.Id] = {
    Try(ju.UUID.fromString(text))
      .toOption
      .toRight(s"Cannot accept $text as ClientSecret.Id")
      .map(ClientSecret.Id(_))
  }

  implicit def clientSecretIdPathBinder(implicit textBinder: PathBindable[String]): PathBindable[ClientSecret.Id] = new PathBindable[ClientSecret.Id] {

    override def bind(key: String, value: String): Either[String, ClientSecret.Id] = {
      textBinder.bind(key, value).flatMap(clientSecretIdFromString(_))
    }

    override def unbind(key: String, clientSecretId: ClientSecret.Id): String = {
      clientSecretId.value.toString()
    }
  }

  implicit def apiVersionQueryStringBindable(implicit textBinder: QueryStringBindable[String]): QueryStringBindable[ApiVersionNbr] = new QueryStringBindable[ApiVersionNbr] {

    override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, ApiVersionNbr]] = {
      for {
        version <- textBinder.bind("version", params)
      } yield {
        version match {
          case Right(version) => Right(ApiVersionNbr(version))
          case _              => Left("Unable to bind an api version")
        }
      }
    }

    override def unbind(key: String, version: ApiVersionNbr): String = {
      textBinder.unbind("version", version.value)
    }
  }

  implicit def environmentPathBinder(implicit textBinder: PathBindable[String]): PathBindable[Environment] = new PathBindable[Environment] {

    override def bind(key: String, value: String): Either[String, Environment] = {
      for {
        text <- textBinder.bind(key, value)
        env  <- Environment.apply(text).toRight("Not a valid environment")
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
          text <- textBinder.bind(key, value)
          mode <- AddTeamMemberPageMode.from(text).toRight("Not a valid AddTeamMemberPageMode")
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
          text <- textBinder.bind(key, value)
          mode <- SaveSubsFieldsPageMode.from(text).toRight("Not a valid SaveSubsFieldsPageMode")
        } yield mode
      }

      override def unbind(key: String, mode: SaveSubsFieldsPageMode): String = {
        mode.toString.toLowerCase
      }
    }

  implicit def mfaTypeQueryStringBindable(implicit textBinder: QueryStringBindable[String]): QueryStringBindable[MfaType] = new QueryStringBindable[MfaType] {

    override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, MfaType]] = {
      for {
        mfaType <- textBinder.bind("mfaType", params)
      } yield {
        mfaType match {
          case Right(mfaType) => Right(MfaType.withNameInsensitive(mfaType))
          case _              => Left("Unable to bind Mfa Type")
        }
      }
    }

    override def unbind(key: String, mfaType: MfaType): String = {
      textBinder.unbind("mfaType", mfaType.toString)
    }
  }

  implicit def mfaActionQueryStringBindable(implicit textBinder: QueryStringBindable[String]): QueryStringBindable[MfaAction] = new QueryStringBindable[MfaAction] {

    override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, MfaAction]] = {
      for {
        mfaAction <- textBinder.bind("mfaAction", params)
      } yield {
        mfaAction match {
          case Right(mfaAction) => Right(MfaAction.withNameInsensitive(mfaAction))
          case _                => Left("Unable to bind Mfa Action")
        }
      }
    }

    override def unbind(key: String, mfaAction: MfaAction): String = {
      textBinder.unbind("mfaAction", mfaAction.toString)
    }
  }

  private def applicationIdFromString(text: String): Either[String, ApplicationId] = {
    ApplicationId.apply(text).toRight(s"Cannot accept $text as ApplicationId")
  }

  implicit def applicationIdPathBinder(implicit textBinder: PathBindable[String]): PathBindable[ApplicationId] = new PathBindable[ApplicationId] {

    override def bind(key: String, value: String): Either[String, ApplicationId] = {
      textBinder.bind(key, value).flatMap(applicationIdFromString)
    }

    override def unbind(key: String, applicationId: ApplicationId): String = {
      applicationId.toString()
    }
  }

  implicit def applicationIdQueryStringBindable(implicit textBinder: QueryStringBindable[String]): QueryStringBindable[ApplicationId] = new QueryStringBindable[ApplicationId] {

    override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, ApplicationId]] = {
      textBinder.bind(key, params).map(_.flatMap(applicationIdFromString))
    }

    override def unbind(key: String, applicationId: ApplicationId): String = {
      textBinder.unbind(key, applicationId.value.toString())
    }
  }
}
