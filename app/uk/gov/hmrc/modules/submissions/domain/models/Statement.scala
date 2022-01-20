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

package uk.gov.hmrc.modules.submissions.domain.models

sealed trait StatementFragment
sealed trait NonBulletStatementFragment extends StatementFragment
sealed trait SimpleStatementFragment extends NonBulletStatementFragment
case class StatementText(text: String) extends SimpleStatementFragment
case class StatementLink(text: String, url: String) extends SimpleStatementFragment

case class StatementBullets(bullets: List[NonBulletStatementFragment]) extends StatementFragment

object StatementBullets {
  def apply(bullets: NonBulletStatementFragment*) = new StatementBullets(bullets.toList)
}

case class CompoundFragment(fragments: List[SimpleStatementFragment]) extends NonBulletStatementFragment

object CompoundFragment {
  def apply(fragments: SimpleStatementFragment*) = new CompoundFragment(fragments.toList)
}

case class Statement(fragments: List[StatementFragment])

object Statement {
  def apply(fragments: StatementFragment*) = new Statement(fragments.toList)
}

