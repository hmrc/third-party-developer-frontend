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

lazy val appName = "third-party-developer-frontend"

lazy val plugins: Seq[Plugins] = Seq(PlayScala, SbtDistributablesPlugin)
lazy val playSettings: Seq[Setting[_]] = Seq.empty

scalaVersion := "2.13.12"

ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision

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
  .configs(IntegrationTest)
  .settings(DefaultBuildSettings.integrationTestSettings())
  .settings(inConfig(IntegrationTest)(scalafixConfigSettings(IntegrationTest)))
  .settings(
    IntegrationTest / testOptions := Seq(Tests.Argument(TestFrameworks.ScalaTest, "-eT")),
    IntegrationTest / unmanagedSourceDirectories ++= Seq(baseDirectory.value / "it", baseDirectory.value / "test-utils"),
    IntegrationTest / unmanagedResourceDirectories += baseDirectory.value / "test",
    IntegrationTest / parallelExecution := false
  )
  .configs(ComponentTest)
  .settings(inConfig(ComponentTest)(Defaults.testSettings ++ BloopDefaults.configSettings ++ ScalafmtPlugin.scalafmtConfigSettings) ++ scalafixConfigSettings(ComponentTest))
  .settings(headerSettings(ComponentTest) ++ automateHeaderSettings(ComponentTest))
  .settings(
    ComponentTest / testOptions := Seq(Tests.Argument(TestFrameworks.ScalaTest, "-eT")),
    ComponentTest / unmanagedSourceDirectories ++= Seq(baseDirectory.value / "component", baseDirectory.value / "test-utils"),
    ComponentTest / unmanagedResourceDirectories += baseDirectory.value / "test",
    ComponentTest / parallelExecution := false
  )
  .settings(majorVersion := 0)
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
lazy val ComponentTest = config("component") extend Test

commands ++= Seq(
  Command.command("run-all-tests") { state => "test" :: "it:test" :: "component:test" :: state },

  Command.command("clean-and-test") { state => "clean" :: "compile" :: "run-all-tests" :: state },

  // Coverage does not need compile !
  Command.command("pre-commit") { state => "clean" :: "scalafmtAll" :: "scalafixAll" :: "coverage" :: "testOnly * -- -l ExcludeFromCoverage" :: "it:test" :: "component:test" :: "coverageOff" :: "testOnly * -- -n ExcludeFromCoverage" :: "coverageReport" :: state }

  )