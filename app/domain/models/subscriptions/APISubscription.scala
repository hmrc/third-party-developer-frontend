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

package uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.subscriptions

import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.{ApiVersion, APIStatus, APIAccess, ApiVersionDefinition, ApiContext}
import uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions.ApiIdentifier

case class ApiCategory(value: String) extends AnyVal

object ApiCategory {
  val EXAMPLE = ApiCategory("EXAMPLE")
}

case class VersionSubscription(version: ApiVersionDefinition, subscribed: Boolean)

case class VersionData(status: APIStatus, access: APIAccess)

case class ApiData(
    serviceName: String,
    name: String,
    isTestSupport: Boolean,
    versions: Map[ApiVersion, VersionData],
    categories: List[ApiCategory])

object ApiData {
  def filterApis(contextFilter: ApiData => Boolean)(in: Map[ApiContext,ApiData]): Map[ApiContext,ApiData] = {
    in.filter {
      case (c,d) => contextFilter(d)
    }
  }

  def filterApis(contextFilter: ApiData => Boolean, versionFilter: VersionData => Boolean)(in: Map[ApiContext,ApiData]): Map[ApiContext,ApiData] = {
    def filterVersions(in: Map[ApiVersion,VersionData]): Map[ApiVersion,VersionData] = {
      in.filter {
        case (v,d) => versionFilter(d)
      }
    }

    val empty = Map.empty[ApiContext, ApiData]

    in.flatMap {
      case (c,d) =>
        val filteredVersions = filterVersions(d.versions)
        if(contextFilter(d) && filteredVersions.nonEmpty) {
          Map(c -> d.copy(versions = filteredVersions))
        } else {
          empty
        }
    }
  }

  def toApiIdentifiers(in: Map[ApiContext,ApiData]): Set[ApiIdentifier] = {
    in.flatMap {
      case (c,d) => d.versions.keySet.map(v => ApiIdentifier(c,v))
    }.toSet
  }
}