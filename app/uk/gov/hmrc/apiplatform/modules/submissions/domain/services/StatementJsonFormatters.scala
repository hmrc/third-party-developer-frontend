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

package uk.gov.hmrc.apiplatform.modules.submissions.domain.services

import uk.gov.hmrc.apiplatform.modules.common.services.NonEmptyListFormatters


trait StatementJsonFormatters extends NonEmptyListFormatters {
  import uk.gov.hmrc.apiplatform.modules.submissions.domain.models._
  import play.api.libs.json._
  import play.api.libs.functional.syntax._
  import uk.gov.hmrc.play.json.Union

  implicit val jsonFormatStatementText = Json.format[StatementText]
  implicit val jsonFormatStatementLink = Json.format[StatementLink]

  implicit lazy val readsStatementBullets: Reads[StatementBullets] = (
      ( __ \ "bullets" ).read(nelReads[NonBulletStatementFragment])
  )
  .map(StatementBullets(_))

  implicit lazy val writesStatementBullets: OWrites[StatementBullets] = (
    (
      (__ \ "bullets").write(nelWrites[NonBulletStatementFragment])
    )
    .contramap(unlift(StatementBullets.unapply))
  )

  implicit lazy val jsonFormatStatementBullets: OFormat[StatementBullets] = OFormat(readsStatementBullets, writesStatementBullets)

  implicit lazy val readsCompoundFragment: Reads[CompoundFragment] = (
    ( __ \ "fragments" ).read(nelReads[SimpleStatementFragment])
  )
  .map(CompoundFragment(_))

  implicit lazy val writesCompoundFragment: OWrites[CompoundFragment] = (
    (
      (__ \ "fragments").write(nelWrites[SimpleStatementFragment])
    )
    .contramap (unlift(CompoundFragment.unapply))
  )

  implicit lazy val jsonFormatCompoundFragment: OFormat[CompoundFragment] = OFormat(readsCompoundFragment, writesCompoundFragment)

  implicit lazy val jsonFormatSimpleStatementFragment: Format[SimpleStatementFragment] = Union.from[SimpleStatementFragment]("statementType")
    .and[StatementText]("text")
    .and[StatementLink]("link")
    .format

  implicit lazy val jsonFormatNonBulletStatementFragment: Format[NonBulletStatementFragment] = Union.from[NonBulletStatementFragment]("statementType")
    .and[StatementText]("text")
    .and[StatementLink]("link")
    .andLazy[CompoundFragment]("compound", jsonFormatCompoundFragment)
    .format

  implicit lazy val jsonFormatStatementFragment: Format[StatementFragment] = Union.from[StatementFragment]("statementType")
    .and[StatementText]("text")
    .and[StatementLink]("link")
    .andLazy[StatementBullets]("bullets", jsonFormatStatementBullets)
    .andLazy[CompoundFragment]("compound", jsonFormatCompoundFragment)
    .format

  implicit val jsonFormatStatement = Json.format[Statement]
}

object StatementJsonFormatters extends StatementJsonFormatters