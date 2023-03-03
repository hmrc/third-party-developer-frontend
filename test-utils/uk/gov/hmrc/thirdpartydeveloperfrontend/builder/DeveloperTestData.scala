package uk.gov.hmrc.thirdpartydeveloperfrontend.builder

import uk.gov.hmrc.apiplatform.modules.common.domain.models.LaxEmailAddress.StringSyntax
import uk.gov.hmrc.thirdpartydeveloperfrontend.builder.DeveloperBuilder
import uk.gov.hmrc.thirdpartydeveloperfrontend.utils.UserIdTracker

trait DeveloperTestData extends DeveloperBuilder {
  self : UserIdTracker =>

  lazy val JoeBloggs = buildDeveloper("developer@example.com".toLaxEmail, "Joe", "Bloggs")
}