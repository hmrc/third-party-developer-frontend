import com.typesafe.sbt.digest.Import._
import com.typesafe.sbt.uglify.Import._
import com.typesafe.sbt.web.Import._
import net.ground5hark.sbt.concat.Import._
import play.core.PlayVersion
import play.sbt.PlayImport._
import play.sbt.routes.RoutesKeys.routesGenerator
import sbt.Keys._
import sbt.Tests.{Group, SubProcess}
import sbt._
import uk.gov.hmrc.DefaultBuildSettings._
import uk.gov.hmrc.PublishingSettings._
import uk.gov.hmrc.SbtAutoBuildPlugin
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._

import scala.util.Properties

lazy val appName = "third-party-developer-frontend"

lazy val appDependencies: Seq[ModuleID] = compile ++ test

lazy val cucumberVersion = "1.2.5"
lazy val seleniumVersion = "2.53.1"
lazy val enumeratumVersion = "1.5.11"

lazy val compile = Seq(
  ws,
  "uk.gov.hmrc" %% "bootstrap-play-25" % "5.1.0",
  "uk.gov.hmrc" %% "govuk-template" % "5.52.0-play-25",
  "uk.gov.hmrc" %% "play-ui" % "8.8.0-play-25",
  "uk.gov.hmrc" %% "url-builder" % "3.3.0-play-25",
  "uk.gov.hmrc" %% "play-json-union-formatter" % "1.7.0",
  "uk.gov.hmrc" %% "http-metrics" % "1.4.0",
  "uk.gov.hmrc" %% "json-encryption" % "4.5.0-play-25",
  "uk.gov.hmrc" %% "emailaddress" % "3.4.0",
  "uk.gov.hmrc" %% "play-conditional-form-mapping" % "1.2.0-play-25",
  "com.beachape" %% "enumeratum" % enumeratumVersion,
  "com.beachape" %% "enumeratum-play" % enumeratumVersion,
  "com.google.zxing" % "core" % "3.2.1",
  "org.typelevel" %% "cats-core" % "2.0.0"
)

val testScope = "test, it"
lazy val test = Seq(
  "info.cukes" %% "cucumber-scala" % cucumberVersion % testScope,
  "info.cukes" % "cucumber-junit" % cucumberVersion % testScope,
  "uk.gov.hmrc" %% "hmrctest" % "3.9.0-play-25" % testScope,
  "junit" % "junit" % "4.12" % testScope,
  "org.jsoup" % "jsoup" % "1.10.2" % testScope,
  "org.pegdown" % "pegdown" % "1.6.0" % testScope,
  "com.typesafe.play" %% "play-test" % PlayVersion.current % testScope,
  "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.1" % testScope,
  "org.seleniumhq.selenium" % "selenium-java" % seleniumVersion % testScope,
  "com.github.tomakehurst" % "wiremock" % "1.58" % testScope,
  "org.mockito" % "mockito-core" % "2.23.0" % testScope,
  "org.scalaj" %% "scalaj-http" % "2.3.0" % testScope,
  "org.scalacheck" %% "scalacheck" % "1.13.5" % testScope,
  // batik-bridge has a circular dependency on itself via transitive batik-script. Avoid that to work with updated build tools
  //[warn] circular dependency found: batik#batik-bridge;1.6-1->batik#batik-script;1.6-1->...
  //[warn] circular dependency found: batik#batik-script;1.6-1->batik#batik-bridge;1.6-1->...
    "com.github.mkolisnyk" % "cucumber-runner" % "1.0.9" % testScope exclude("batik", "batik-script"),
    "batik" % "batik-script" % "1.6-1" % testScope exclude("batik", "batik-bridge"),
  "net.masterthought" % "cucumber-reporting" % "3.3.0" % testScope,
  "net.masterthought" % "cucumber-sandwich" % "3.3.0" % testScope,
  "com.assertthat" % "selenium-shutterbug" % "0.2" % testScope
)
lazy val overrideDependencies = Set(
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
    scalaVersion := "2.11.12",
    resolvers += Resolver.jcenterRepo,
    routesImport += "connectors.binders._"
  )
  .settings(playPublishingSettings: _*)
  .settings(inConfig(TemplateTest)(Defaults.testSettings): _*)
  .settings(
    unmanagedSourceDirectories in Test := (baseDirectory in Test) (base => Seq(base / "test", base / "test-utils")).value,
    testOptions in Test := Seq(Tests.Argument(TestFrameworks.ScalaTest, "-eT"))
  )
  .configs(IntegrationTest)
  .settings(inConfig(IntegrationTest)(Defaults.itSettings): _*)
  .settings(
    testOptions in IntegrationTest := Seq(Tests.Argument(TestFrameworks.ScalaTest, "-eT")),
    unmanagedSourceDirectories in IntegrationTest := (baseDirectory in IntegrationTest) (base => Seq(base / "it", base / "test-utils")).value,
    unmanagedResourceDirectories in IntegrationTest := (baseDirectory in IntegrationTest) (base => Seq(base / "test")).value,
    parallelExecution in IntegrationTest := false
  )
  .configs(ComponentTest)
  .settings(inConfig(ComponentTest)(Defaults.testSettings): _*)
  .settings(
    testOptions in ComponentTest := Seq(Tests.Argument(TestFrameworks.ScalaTest, "-eT")),
    unmanagedSourceDirectories in ComponentTest := (baseDirectory in ComponentTest) (base => Seq(base / "component", base / "test-utils")).value,
    unmanagedResourceDirectories in ComponentTest := (baseDirectory in ComponentTest) (base => Seq(base / "test")).value,
    unmanagedResourceDirectories in ComponentTest += baseDirectory(_ / "target/web/public/test").value,
    testOptions in ComponentTest += Tests.Setup(() => System.setProperty("javascript.enabled", "true")),
    testGrouping in ComponentTest := oneForkedJvmPerTest((definedTests in ComponentTest).value),
    parallelExecution in ComponentTest := false
  )
  .settings(majorVersion := 0)
  .settings(scalacOptions ++= Seq("-Ypartial-unification"))

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

def oneForkedJvmPerTest(tests: Seq[TestDefinition]) =
  tests map { test =>
    Group(
      test.name,
      Seq(test),
      SubProcess(
        ForkOptions(
          runJVMOptions = Seq(
            "-Dtest.name=" + test.name,
            s"-Dtest_driver=${Properties.propOrElse("test_driver", "chrome")}"
          )
        )
      )
    )
  }

// Coverage configuration
coverageMinimum := 85
coverageFailOnMinimum := true
coverageExcludedPackages := "<empty>;com.kenshoo.play.metrics.*;.*definition.*;prod.*;app.*;uk.gov.hmrc.BuildInfo"

