package repositories

import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{OptionValues, BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatest.matchers.should.Matchers

trait BaseRepositoryIntegrationSpec
  extends AnyWordSpec
    with Matchers
    with OptionValues
    with DefaultAwaitTimeout
    with FutureAwaits
    with BeforeAndAfterAll
    with BeforeAndAfterEach
