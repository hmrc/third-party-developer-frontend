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
import uk.gov.hmrc.PublishingSettings._
import uk.gov.hmrc.SbtAutoBuildPlugin
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._

import bloop.integrations.sbt.BloopDefaults

import scala.util.Properties

lazy val appName = "third-party-developer-frontend"

lazy val plugins: Seq[Plugins] = Seq(PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin, SbtArtifactory)
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
    dependencyOverrides ++= AppDependencies.overrideDependencies,
    parallelExecution in Test := false,
    fork in Test := false,
    retrieveManaged := true,
    routesGenerator := InjectedRoutesGenerator,
    scalaVersion := "2.12.11",
    routesImport += "controllers.binders._"
  )
  .settings(
    resolvers := Seq(
      Resolver.bintrayRepo("hmrc", "releases"),
      Resolver.typesafeRepo("releases"),
      Resolver.jcenterRepo
    )
  )
  .settings(SilencerSettings())
  .settings(playPublishingSettings: _*)
  .settings(inConfig(TemplateTest)(Defaults.testSettings): _*)
  .settings(
    Test / unmanagedSourceDirectories := (baseDirectory in Test)(base => Seq(base / "test", base / "test-utils")).value,
    Test / testOptions := Seq(Tests.Argument(TestFrameworks.ScalaTest, "-eT"))
  )
  .configs(IntegrationTest)
  .settings(inConfig(IntegrationTest)(Defaults.itSettings): _*)
  .settings(
    IntegrationTest / testOptions := Seq(Tests.Argument(TestFrameworks.ScalaTest, "-eT")),
    IntegrationTest / unmanagedSourceDirectories := (baseDirectory in IntegrationTest)(base => Seq(base / "it", base / "test-utils")).value,
    IntegrationTest / unmanagedResourceDirectories := (baseDirectory in IntegrationTest)(base => Seq(base / "test")).value,
    IntegrationTest / parallelExecution := false
  )
  .configs(ComponentTest)
  .settings(inConfig(ComponentTest)(Defaults.testSettings): _*)
  .settings(inConfig(ComponentTest)(BloopDefaults.configSettings))
  .settings(
    ComponentTest / testOptions := Seq(Tests.Argument(TestFrameworks.ScalaTest, "-eT")),
    ComponentTest / unmanagedSourceDirectories := (baseDirectory in ComponentTest)(base => Seq(base / "component", base / "test-utils")).value,
    ComponentTest / unmanagedResourceDirectories := (baseDirectory in ComponentTest)(base => Seq(base / "test")).value,
    ComponentTest / unmanagedResourceDirectories += baseDirectory(_ / "target/web/public/test").value,
    ComponentTest / testOptions += Tests.Setup(() => System.setProperty("javascript.enabled", "true")),
    ComponentTest / testGrouping := oneForkedJvmPerTest((definedTests in ComponentTest).value),
    ComponentTest / parallelExecution := false
  )
  .settings(majorVersion := 0)
  .settings(scalacOptions ++= Seq("-Ypartial-unification"))

lazy val allPhases = "tt->test;test->test;test->compile;compile->compile"
lazy val IntegrationTest = config("it") extend Test
lazy val ComponentTest = config("component") extend Test
lazy val TemplateTest = config("tt") extend Test
lazy val playPublishingSettings: Seq[sbt.Setting[_]] = Seq(
  credentials += SbtCredentials,
  publishArtifact in (Compile, packageDoc) := false,
  publishArtifact in (Compile, packageSrc) := false
) ++
  publishAllArtefacts

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
