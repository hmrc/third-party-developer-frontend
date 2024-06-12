import com.typesafe.sbt.digest.Import._
import com.typesafe.sbt.uglify.Import._
import com.typesafe.sbt.web.Import._
import net.ground5hark.sbt.concat.Import._
import uk.gov.hmrc.DefaultBuildSettings

lazy val appName = "third-party-developer-frontend"

Global / bloopAggregateSourceDependencies := true
Global / bloopExportJarClassifiers := Some(Set("sources"))

ThisBuild / scalaVersion := "2.13.12"
ThisBuild / majorVersion := 0
ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision
ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always

lazy val microservice = Project(appName, file("."))
  .enablePlugins(PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin)
  .settings(
    Concat.groups := Seq(
      "javascripts/apis-app.js" -> group(
        (baseDirectory.value / "app" / "assets" / "javascripts" / "combine") ** "*.js"
      )
    ),
    uglifyCompressOptions := Seq(
      "unused=true",
      "dead_code=true"
    ),
    uglify / includeFilter := GlobFilter("apis-*.js"),
    pipelineStages := Seq(digest),
    Assets / pipelineStages := Seq(
      concat,
      uglify
    )
  )
  .settings(ScoverageSettings())
  .settings(
    libraryDependencies ++= AppDependencies(),
    retrieveManaged := true
  )
  .settings(
    resolvers += Resolver.typesafeRepo("releases")
  )
  .settings(
    Test / parallelExecution := false,
    Test / fork := false,
    Test / unmanagedSourceDirectories += baseDirectory.value / "test-utils",
    Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-eT")
  )
  .settings(
      routesImport ++= Seq(
        "uk.gov.hmrc.thirdpartydeveloperfrontend.controllers.binders._",
        "uk.gov.hmrc.apiplatform.modules.uplift.controllers._",
        "uk.gov.hmrc.apiplatform.modules.submissions.controllers.binders._",
        "uk.gov.hmrc.apiplatform.modules.submissions.domain.models._",
        "uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.controllers._",
        "uk.gov.hmrc.thirdpartydeveloperfrontend.domain.models.applications._",
        "uk.gov.hmrc.apiplatform.modules.apis.domain.models._",
        "uk.gov.hmrc.apiplatform.modules.common.domain.models._",
        "uk.gov.hmrc.apiplatform.modules.applications.core.domain.models._",
        "uk.gov.hmrc.apiplatform.modules.applications.submissions.domain.models._"
    )
  )
  .settings(
    TwirlKeys.templateImports ++= Seq(
      "uk.gov.hmrc.hmrcfrontend.views.html.components._",
      "uk.gov.hmrc.hmrcfrontend.views.html.helpers._",
      "uk.gov.hmrc.thirdpartydeveloperfrontend.controllers",
      "uk.gov.hmrc.thirdpartydeveloperfrontend.config.ApplicationConfig",
      "uk.gov.hmrc.apiplatform.modules.applications.core.domain.models.ClientSecret.Id",
      "uk.gov.hmrc.apiplatform.modules.applications.access.domain.models._",
      "uk.gov.hmrc.apiplatform.modules.apis.domain.models._",
      "uk.gov.hmrc.apiplatform.modules.common.domain.models._"
    )
  )
  .settings(
    scalacOptions ++= Seq(
      "-Wconf:cat=unused&src=views/.*\\.scala:s",
      // https://www.scala-lang.org/2021/01/12/configuring-and-suppressing-warnings.html
      // suppress warnings in generated routes files
      "-Wconf:src=routes/.*:s"
    )
  )

lazy val it = (project in file("it"))
  .enablePlugins(PlayScala)
  .dependsOn(microservice % "test->test")
  .settings(
    name := "integration-tests",
    DefaultBuildSettings.itSettings()
  )

lazy val component = (project in file("component"))
  .dependsOn(microservice % "test->test")
  .settings(
    name := "component-tests",
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test ++ AppDependencies.componentTestDependencies,
    Test / unmanagedResourceDirectories += baseDirectory.value / "resources",
    DefaultBuildSettings.itSettings(),
    Test / testOptions := Seq(Tests.Argument(TestFrameworks.JUnit, "-a"))
  )

commands ++= Seq(
  Command.command("cleanAll") { state => "clean" :: "it/clean" :: "component/clean" :: state },
  Command.command("fmtAll") { state => "scalafmtAll" :: "it/scalafmtAll" :: "component/scalafmtAll" :: state },
  Command.command("fixAll") { state => "scalafixAll" :: "it/scalafixAll" :: "component/scalafixAll" :: state },
  Command.command("testAllIncludedInCoverage") { state => "testOnly * -- -l ExcludeFromCoverage" :: "it/test" :: "component/test" :: state },
  Command.command("testAllExcludedFromCoverage") { state => "testOnly * -- -n ExcludeFromCoverage" :: state },
  Command.command("testAll") { state => "test" :: "it/test" :: "component/test" :: state },
  Command.command("run-all-tests") { state => "testAll" :: state },
  Command.command("clean-and-test") { state => "cleanAll" :: "compile" :: "run-all-tests" :: state },
  Command.command("pre-commit") { state => "cleanAll" :: "fmtAll" :: "fixAll" :: "testAllExcludedFromCoverage" :: "coverage" :: "testAllIncludedInCoverage" :: "coverageOff" :: "coverageAggregate" :: state }
)
