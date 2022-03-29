import com.typesafe.sbt.digest.Import._
import com.typesafe.sbt.uglify.Import._
import com.typesafe.sbt.web.Import._
import net.ground5hark.sbt.concat.Import._
import play.core.PlayVersion
import play.sbt.PlayImport._
import play.sbt.routes.RoutesKeys.routesGenerator
import sbt.Keys._
import sbt.Tests.{Group, SubProcess}
import sbt.{Resolver, _}
import uk.gov.hmrc.DefaultBuildSettings._
import uk.gov.hmrc.SbtAutoBuildPlugin
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._

import bloop.integrations.sbt.BloopDefaults

bloopAggregateSourceDependencies in Global := true

import scala.util.Properties

lazy val appName = "third-party-developer-frontend"

lazy val plugins: Seq[Plugins] = Seq(PlayScala, SbtAutoBuildPlugin, SbtDistributablesPlugin)
lazy val playSettings: Seq[Setting[_]] = Seq.empty

lazy val microservice = Project(appName, file("."))
  .enablePlugins(plugins: _*)
  .settings(
    Concat.groups := Seq(
      "javascripts/apis-app.js" -> group(
        (baseDirectory.value / "app" / "assets" / "javascripts") ** "*.js"
      )
    ),
    uglifyCompressOptions := Seq(
      "unused=true",
      "dead_code=true"
    ),
    includeFilter in uglify := GlobFilter("apis-*.js"),
    pipelineStages := Seq(digest),
    pipelineStages in Assets := Seq(
      concat,
      uglify
    )
  )
  .settings(playSettings: _*)
  .settings(scalaSettings: _*)
  .settings(publishingSettings: _*)
  .settings(defaultSettings(): _*)
  .settings(ScoverageSettings(): _*)
  .settings(
    targetJvm := "jvm-1.8",
    libraryDependencies ++= AppDependencies(),
    retrieveManaged := true,
    routesGenerator := InjectedRoutesGenerator,
    scalaVersion := "2.12.12"
  )
  .settings(
    resolvers += Resolver.typesafeRepo("releases")
  )
  .settings(SilencerSettings())
  .settings(
    Test / parallelExecution := false,
    Test / fork := false,
    Test / unmanagedSourceDirectories ++= Seq(baseDirectory.value / "test", baseDirectory.value / "test-utils"),
    Test / testOptions := Seq(Tests.Argument(TestFrameworks.ScalaTest, "-eT"))
  )
  .configs(IntegrationTest)
  .settings(inConfig(IntegrationTest)(Defaults.itSettings): _*)
  .settings(inConfig(IntegrationTest)(BloopDefaults.configSettings))
  .settings(
    IntegrationTest / testOptions := Seq(Tests.Argument(TestFrameworks.ScalaTest, "-eT")),
    IntegrationTest / unmanagedSourceDirectories ++= Seq(baseDirectory.value / "it", baseDirectory.value / "test-utils"),
    IntegrationTest / unmanagedResourceDirectories += baseDirectory.value / "test",
    IntegrationTest / parallelExecution := false
  )
  .configs(ComponentTest)
  .settings(inConfig(ComponentTest)(Defaults.testSettings): _*)
  .settings(inConfig(ComponentTest)(BloopDefaults.configSettings))
  .settings(
    ComponentTest / testOptions := Seq(Tests.Argument(TestFrameworks.ScalaTest, "-eT")),
    ComponentTest / unmanagedSourceDirectories ++= Seq(baseDirectory.value / "component", baseDirectory.value / "test-utils"),
    ComponentTest / unmanagedResourceDirectories += baseDirectory.value / "test",
    ComponentTest / unmanagedResourceDirectories += baseDirectory.value / "target" / "web" / "public" / "test",
    ComponentTest / testOptions += Tests.Setup(() => System.setProperty("javascript.enabled", "true")),
    ComponentTest / testGrouping := oneForkedJvmPerTest((definedTests in ComponentTest).value),
    ComponentTest / parallelExecution := false
  )
  .settings(majorVersion := 0)
  .settings(scalacOptions ++= Seq("-Ypartial-unification"))
  .settings(
      routesImport ++= Seq(
        "uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.binders._",
        "uk.gov.hmrc.apiplatform.modules.uplift.controllers._",
        "uk.gov.hmrc.apiplatform.modules.submissions.controllers.binders._",
        "uk.gov.hmrc.apiplatform.modules.submissions.domain.models._",
        "uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers._",
        "uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._",
        "uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.apidefinitions._"
    )
  )
  .settings(
    TwirlKeys.templateImports ++= Seq(
      "uk.gov.hmrc.govukfrontend.views.html.components._",
      "uk.gov.hmrc.hmrcfrontend.views.html.components._",
      "uk.gov.hmrc.hmrcfrontend.views.html.helpers._",
      "uk.gov.hmrc.thirdpartydeveloperfrontend.controllers",
      "uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig"
    )
  )

lazy val ComponentTest = config("component") extend Test
lazy val TemplateTest = config("tt") extend Test

def oneForkedJvmPerTest(tests: Seq[TestDefinition]): Seq[Group] =
  tests map { test =>
    Group(
      test.name,
      Seq(test),
      SubProcess(
        ForkOptions().withRunJVMOptions(Vector(s"-Dtest.name={test.name}", s"-Dtest_driver=${Properties.propOrElse("test_driver", "chrome")}"))
      )
    )
  }
