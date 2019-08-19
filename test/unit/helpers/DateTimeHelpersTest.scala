package unit.helpers

import helpers.DateTimeHelpers
import org.joda.time.LocalDate
import org.scalatest.{Matchers, WordSpec}

class DateTimeHelpersTest extends WordSpec with Matchers {
  "an empty date value" should {
    "parse to None" in {
      DateTimeHelpers.parseLocalDate(Some("")) shouldBe None
    }
  }

  "an whitespace date value" should {
    "parse to None" in {
      DateTimeHelpers.parseLocalDate(Some(" ")) shouldBe None
    }
  }

  "an None date value" should {
    "parse to None" in {
      DateTimeHelpers.parseLocalDate(None) shouldBe None
    }
  }

  "the date 2001-02-03" should {
    "parse to a local 'Europe/London' date" in {
      DateTimeHelpers.parseLocalDate(Some("2001-02-03")) shouldBe (Some(new LocalDate(2001,2,3)))
    }
  }
}
