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

import scala.util.Properties
import bloop.integrations.sbt.BloopDefaults

lazy val appName = "third-party-developer-frontend"

lazy val appDependencies: Seq[ModuleID] = compile ++ test

lazy val cucumberVersion = "6.2.2"
lazy val seleniumVersion = "2.53.1"
lazy val enumeratumVersion = "1.5.12"

val testScope = "test, it"

lazy val compile = Seq(
  ws,
  "uk.gov.hmrc" %% "bootstrap-play-26" % "1.8.0",
  "uk.gov.hmrc" %% "govuk-template" % "5.55.0-play-26",
  "uk.gov.hmrc" %% "play-ui" % "8.11.0-play-26",
  "uk.gov.hmrc" %% "url-builder" % "3.4.0-play-26",
  "uk.gov.hmrc" %% "play-json-union-formatter" % "1.11.0",
  "uk.gov.hmrc" %% "http-metrics" % "1.10.0",
  "uk.gov.hmrc" %% "json-encryption" % "4.8.0-play-26",
  "uk.gov.hmrc" %% "emailaddress" % "3.4.0",
  "uk.gov.hmrc" %% "play-conditional-form-mapping" % "1.2.0-play-26",
  "com.beachape" %% "enumeratum" % enumeratumVersion,
  "com.beachape" %% "enumeratum-play" % enumeratumVersion,
  "com.google.zxing" % "core" % "3.2.1",
  "org.typelevel" %% "cats-core" % "2.0.0",
  "com.typesafe.play" %% "play-json" % "2.7.4",
  "com.typesafe.play" %% "play-json-joda" % "2.7.4"
)

lazy val test = Seq(
  "io.cucumber" %% "cucumber-scala" % cucumberVersion % testScope,
  "io.cucumber" % "cucumber-junit" % cucumberVersion % testScope,
  "io.cucumber" % "cucumber-java8" % cucumberVersion % testScope,
  "uk.gov.hmrc" %% "hmrctest" % "3.9.0-play-26" % testScope,
  "junit" % "junit" % "4.12" % testScope,
  "org.jsoup" % "jsoup" % "1.10.2" % testScope,
  "org.pegdown" % "pegdown" % "1.6.0" % testScope,
  "com.typesafe.play" %% "play-test" % PlayVersion.current % testScope,
  "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.3" % testScope,
  "org.seleniumhq.selenium" % "selenium-java" % seleniumVersion % testScope,
  "com.github.tomakehurst" % "wiremock" % "1.58" % testScope,
  "org.mockito" % "mockito-core" % "2.23.0" % testScope,
  "org.scalaj" %% "scalaj-http" % "2.3.0" % testScope,
  "org.scalacheck" %% "scalacheck" % "1.13.5" % testScope,
  // batik-bridge has a circular dependency on itself via transitive batik-script. Avoid that to work with updated build tools
  //[warn] circular dependency found: batik#batik-bridge;1.6-1->batik#batik-script;1.6-1->...
  //[warn] circular dependency found: batik#batik-script;1.6-1->batik#batik-bridge;1.6-1->...
  // "batik" % "batik-script" % "1.6-1" % testScope exclude("batik", "batik-bridge"),
  // "com.github.mkolisnyk" % "cucumber-runner" % "1.3.5" % testScope exclude("batik", "batik-script"),
  "com.assertthat" % "selenium-shutterbug" % "0.2" % testScope
)

lazy val overrideDependencies = Seq(
  "org.seleniumhq.selenium" % "selenium-java" % seleniumVersion % testScope,
  "org.seleniumhq.selenium" % "selenium-htmlunit-driver" % seleniumVersion % testScope
)

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
  .settings(
    targetJvm := "jvm-1.8",
    libraryDependencies ++= appDependencies,
    dependencyOverrides ++= overrideDependencies,
    parallelExecution in Test := false,
    fork in Test := false,
    retrieveManaged := true,
    routesGenerator := InjectedRoutesGenerator,
    scalaVersion := "2.12.11",
    routesImport += "connectors.binders._"
  )
  .settings(
    resolvers := Seq(
      Resolver.bintrayRepo("hmrc", "releases"),
      Resolver.typesafeRepo("releases"),
      Resolver.jcenterRepo
    )
  )
  .settings(playPublishingSettings: _*)
  .settings(inConfig(TemplateTest)(Defaults.testSettings): _*)
  .settings(
    Test / unmanagedSourceDirectories := (baseDirectory in Test) (base => Seq(base / "test", base / "test-utils")).value,
    Test / testOptions := Seq(Tests.Argument(TestFrameworks.ScalaTest, "-eT"))
  )
  .configs(IntegrationTest)
  .settings(inConfig(IntegrationTest)(Defaults.itSettings): _*)
  .settings(
    IntegrationTest / testOptions := Seq(Tests.Argument(TestFrameworks.ScalaTest, "-eT")),
    IntegrationTest / unmanagedSourceDirectories := (baseDirectory in IntegrationTest) (base => Seq(base / "it", base / "test-utils")).value,
    IntegrationTest / unmanagedResourceDirectories := (baseDirectory in IntegrationTest) (base => Seq(base / "test")).value,
    IntegrationTest / parallelExecution := false
  )
  .configs(ComponentTest)
  .settings(inConfig(ComponentTest)(Defaults.testSettings): _*)
  .settings(
    ComponentTest / testOptions := Seq(Tests.Argument(TestFrameworks.ScalaTest, "-eT")),
    ComponentTest / unmanagedSourceDirectories := (baseDirectory in ComponentTest) (base => Seq(base / "component", base / "test-utils")).value,
    ComponentTest / unmanagedResourceDirectories := (baseDirectory in ComponentTest) (base => Seq(base / "test")).value,
    ComponentTest / unmanagedResourceDirectories += baseDirectory(_ / "target/web/public/test").value,
    ComponentTest / testOptions += Tests.Setup(() => System.setProperty("javascript.enabled", "true")),
    ComponentTest / testGrouping := oneForkedJvmPerTest((definedTests in ComponentTest).value),
    ComponentTest / parallelExecution := false
  )
  .settings(majorVersion := 0)
  .settings(scalacOptions ++= Seq("-Ypartial-unification"))
  .settings(logLevel := Level.Error)
  .settings(
    inConfig(IntegrationTest)(BloopDefaults.configSettings),
    inConfig(TemplateTest)(BloopDefaults.configSettings),
    inConfig(ComponentTest)(BloopDefaults.configSettings),
  )

lazy val allPhases = "tt->test;test->test;test->compile;compile->compile"
lazy val IntegrationTest = config("it") extend Test
lazy val ComponentTest = config("component") extend Test
lazy val TemplateTest = config("tt") extend Test
lazy val playPublishingSettings: Seq[sbt.Setting[_]] = Seq(

  credentials += SbtCredentials,

  publishArtifact in(Compile, packageDoc) := false,
  publishArtifact in(Compile, packageSrc) := false
) ++
  publishAllArtefacts

// Note that this task has to be scoped globally
bloopAggregateSourceDependencies in Global := true

def oneForkedJvmPerTest(tests: Seq[TestDefinition]): Seq[Group] =
  tests map { test =>
    Group(
      test.name,
      Seq(test),
      SubProcess(
        ForkOptions().withRunJVMOptions(
          Vector(s"-Dtest.name={test.name}", s"-Dtest_driver=${Properties.propOrElse("test_driver", "chrome")}"))
      )
    )
  }

// Coverage configuration
coverageMinimum := 85
coverageFailOnMinimum := true
coverageExcludedPackages := "<empty>;com.kenshoo.play.metrics.*;.*definition.*;prod.*;app.*;uk.gov.hmrc.BuildInfo"

