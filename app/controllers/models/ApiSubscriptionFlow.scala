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

package controllers.models

import domain.models.apidefinitions.{ApiContext, ApiVersion, ApiIdentifier}

  case class ApiSubscriptionsFlow(subscriptions: Map[ApiIdentifier, Boolean]) {
    def isSelected(id: ApiIdentifier): Boolean = 
      subscriptions.get(id).getOrElse(false)
  }

  object ApiSubscriptionsFlow {
    def allOf(in: Set[ApiIdentifier]): ApiSubscriptionsFlow =
      ApiSubscriptionsFlow(in.map(k => k -> true).toMap)

    def toSessionString(id: ApiIdentifier): String =
      s"(${id.context.value},${id.version.value})"
    
    def toSessionString(bool: Boolean): String =
      if( bool ) "on" else "off"

    def toSessionString( tuple: (ApiIdentifier, Boolean) ): String =
      s"${toSessionString(tuple._1)}=${toSessionString(tuple._2)}"

    def toSessionString(in: ApiSubscriptionsFlow): String = {
      in.subscriptions
      .map(toSessionString)
      .mkString("[", "@@", "]")
    }

    def idFromSessionString(in: String): ApiIdentifier = {
      val c :: v :: Nil = in.drop(1).dropRight(1).split(",").toList
      ApiIdentifier(ApiContext(c), ApiVersion(v))
    }

    def boolFromSessionString(in: String): Boolean = in == "on"

    def pairFromSessionString(in: String): (ApiIdentifier, Boolean) = {
      val idText :: onOffText :: Nil =
        in
        .split("=")
        .toList

      val id: ApiIdentifier = idFromSessionString(idText)
      val bool: Boolean = boolFromSessionString(onOffText)
      (id, bool)
    }

    def fromSessionString(in: String): ApiSubscriptionsFlow = {
      ApiSubscriptionsFlow(
        in
        .drop(1)
        .dropRight(1)
        .split("@@")
        .toList
        .map(pairFromSessionString)
        .toMap
      )
    }
  }
