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

package uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models

import java.util.UUID

import uk.gov.hmrc.apiplatform.modules.applications.submissions.domain.models.SubmissionId as AppSubmissionId
import uk.gov.hmrc.apiplatform.modules.common.domain.models.{ApiContext, ApiVersionNbr, ApplicationId}

// Play's routes compiler cannot bind opaque types directly as route parameters. These wrapper
// types stand in for the real opaque types purely in conf/*.routes; the given Conversions below
// bridge them back to the real types so that controller method signatures remain unchanged.
object RouteModels {

  case class SimpleApplicationId(value: UUID) extends AnyVal {
    override def toString: String = value.toString
  }

  given Conversion[SimpleApplicationId, ApplicationId] = sId => ApplicationId(sId.value)
  given Conversion[ApplicationId, SimpleApplicationId] = id => SimpleApplicationId(id.value)

  case class SimpleApiContext(value: String) extends AnyVal {
    override def toString: String = value
  }

  given Conversion[SimpleApiContext, ApiContext] = sCtx => ApiContext(sCtx.value)
  given Conversion[ApiContext, SimpleApiContext] = ctx => SimpleApiContext(ctx.value)

  case class SimpleApiVersionNbr(value: String) extends AnyVal {
    override def toString: String = value
  }

  given Conversion[SimpleApiVersionNbr, ApiVersionNbr] = sVer => ApiVersionNbr(sVer.value)
  given Conversion[ApiVersionNbr, SimpleApiVersionNbr] = ver => SimpleApiVersionNbr(ver.value)

  case class SimpleSubmissionId(value: UUID) extends AnyVal {
    override def toString: String = value.toString
  }

  given Conversion[SimpleSubmissionId, AppSubmissionId] = sId => AppSubmissionId.unsafeApply(sId.value.toString)
  given Conversion[AppSubmissionId, SimpleSubmissionId] = id => SimpleSubmissionId(id.value)
}
