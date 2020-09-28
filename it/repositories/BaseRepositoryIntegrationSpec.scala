package repositories

import org.scalatest._
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}

trait BaseRepositoryIntegrationSpec
  extends WordSpec
    with Matchers
    with OptionValues
    with DefaultAwaitTimeout
    with FutureAwaits
    with BeforeAndAfterAll
    with BeforeAndAfterEach
