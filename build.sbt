import com.typesafe.sbt.digest.Import._
import com.typesafe.sbt.uglify.Import._
import com.typesafe.sbt.web.Import._
import net.ground5hark.sbt.concat.Import._
import org.scalafmt.sbt.ScalafmtPlugin
import sbt.Keys._
import sbt.{Resolver, _}
import uk.gov.hmrc.DefaultBuildSettings
import uk.gov.hmrc.DefaultBuildSettings._
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin
import bloop.integrations.sbt.BloopDefaults

Global / bloopAggregateSourceDependencies := true
Global / bloopExportJarClassifiers := Some(Set("sources"))

lazy val appName = "third-party-developer-frontend"

lazy val plugins: Seq[Plugins] = Seq(PlayScala, SbtDistributablesPlugin)
lazy val playSettings: Seq[Setting[_]] = Seq.empty

ThisBuild / scalaVersion := "2.13.12"
ThisBuild / majorVersion := 0
ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision
ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always

lazy val microservice = Project(appName, file("."))
  .enablePlugins(plugins: _*)
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
  .settings(playSettings: _*)
  .settings(scalaSettings: _*)
  .settings(defaultSettings(): _*)
  .settings(ScoverageSettings(): _*)
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
    Test / unmanagedSourceDirectories ++= Seq(baseDirectory.value / "test", baseDirectory.value / "test-utils"),
    Test / testOptions := Seq(Tests.Argument(TestFrameworks.ScalaTest, "-eT"))
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
    "-Wconf:cat=unused&src=.*RoutesPrefix\\.scala:s",
    "-Wconf:cat=unused&src=.*Routes\\.scala:s",
    "-Wconf:cat=unused&src=.*ReverseRoutes\\.scala:s"
    )
  )

lazy val it = (project in file("it"))
  .enablePlugins(PlayScala)
  .dependsOn(microservice % "test->test")
  .settings(
    name := "integration-tests",
    Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-eT"),
    DefaultBuildSettings.itSettings()
  )

lazy val component = (project in file("component"))
  .enablePlugins(PlayScala)
  .dependsOn(microservice % "test->test")
  .settings(
    name := "component-tests",
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test ++ AppDependencies.componentTestDependencies,
    Test / testOptions := Seq(Tests.Argument(TestFrameworks.ScalaTest, "-eT")),
    DefaultBuildSettings.itSettings()
  )

commands ++= Seq(
  Command.command("cleanAll") { state => "clean" :: "it/clean" :: "component/clean" :: state },
  Command.command("fmtAll") { state => "scalafmtAll" :: "it/scalafmtAll" :: "component/scalafmtAll" :: state },
  Command.command("fixAll") { state => "scalafixAll" :: "it/scalafixAll" :: "component/scalafixAll" :: state },
  Command.command("testAll") { state => "test" :: "it/test" :: "component/test" :: state },
  Command.command("run-all-tests") { state => "testAll" :: state },
  Command.command("clean-and-test") { state => "clean" :: "compile" :: "run-all-tests" :: state },
  Command.command("pre-commit") { state => "clean" :: "scalafmtAll" :: "scalafixAll" :: "coverage" :: "testOnly * -- -l ExcludeFromCoverage" :: "run-all-tests" :: "coverageOff" :: "coverageAggregate" :: state }
)