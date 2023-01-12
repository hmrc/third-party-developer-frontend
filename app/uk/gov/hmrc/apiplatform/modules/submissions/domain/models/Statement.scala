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

package uk.gov.hmrc.apiplatform.modules.submissions.domain.models

import cats.data.NonEmptyList

sealed trait StatementFragment
sealed trait NonBulletStatementFragment             extends StatementFragment
sealed trait SimpleStatementFragment                extends NonBulletStatementFragment
case class StatementText(text: String)              extends SimpleStatementFragment
case class StatementLink(text: String, url: String) extends SimpleStatementFragment

case class StatementBullets(bullets: NonEmptyList[NonBulletStatementFragment]) extends StatementFragment

object StatementBullets {
  def apply(bullet: NonBulletStatementFragment, bullets: NonBulletStatementFragment*) = new StatementBullets(NonEmptyList.of(bullet, bullets: _*))
}

case class CompoundFragment(fragments: NonEmptyList[SimpleStatementFragment]) extends NonBulletStatementFragment

object CompoundFragment {
  def apply(fragment: SimpleStatementFragment, fragments: SimpleStatementFragment*) = new CompoundFragment(NonEmptyList.of(fragment, fragments: _*))
}

case class Statement(fragments: NonEmptyList[StatementFragment])

object Statement {
  def apply(fragment: StatementFragment, fragments: StatementFragment*) = new Statement(NonEmptyList.of(fragment, fragments: _*))
}
