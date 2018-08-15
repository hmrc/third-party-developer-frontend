import com.typesafe.sbt.digest.Import._
import com.typesafe.sbt.uglify.Import._
import com.typesafe.sbt.web.Import._
import net.ground5hark.sbt.concat.Import._
import play.core.PlayVersion
import play.routes.compiler.StaticRoutesGenerator
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

lazy val appName = "third-party-developer-frontend"

lazy val appDependencies: Seq[ModuleID] = compile ++ test

lazy val t2vVersion = "0.14.2"
lazy val cucumberVersion = "1.2.5"
lazy val seleniumVersion = "2.53.1"
lazy val enumeratumVersion = "1.5.11"

lazy val compile = Seq(
  ws,
  "uk.gov.hmrc" %% "frontend-bootstrap" % "8.26.0",
  "uk.gov.hmrc" %% "url-builder" % "2.1.0",
  "uk.gov.hmrc" %% "play-json-union-formatter" % "1.3.0",
  "uk.gov.hmrc" %% "http-metrics" % "1.2.0",
  "de.threedimensions" %% "metrics-play" % "2.5.13",
  "jp.t2v" %% "play2-auth" % t2vVersion,
  "uk.gov.hmrc" %% "json-encryption" % "3.2.0",
  "uk.gov.hmrc" %% "emailaddress" % "2.2.0",
  "uk.gov.hmrc" %% "play-conditional-form-mapping" % "0.2.0",
  "io.dropwizard.metrics" % "metrics-graphite" % "3.2.1",
  "com.beachape" %% "enumeratum" % enumeratumVersion,
  "com.beachape" %% "enumeratum-play" % enumeratumVersion
)

lazy val test = Seq(
  "info.cukes" %% "cucumber-scala" % cucumberVersion % "test,it",
  "info.cukes" % "cucumber-junit" % cucumberVersion % "test,it",
  "uk.gov.hmrc" %% "hmrctest" % "3.0.0" % "test,it",
  "junit" % "junit" % "4.12" % "test,it",
  "org.jsoup" % "jsoup" % "1.10.2" % "test,it",
  "org.pegdown" % "pegdown" % "1.6.0" % "test,it",
  "com.typesafe.play" %% "play-test" % PlayVersion.current % "test,it",
  "org.scalatest" %% "scalatest" % "2.2.6" % "test,it",
  "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.1" % "test,it",
  "org.seleniumhq.selenium" % "selenium-java" % seleniumVersion % "test,it",
  "com.github.tomakehurst" % "wiremock" % "1.58" % "test,it",
  "org.mockito" % "mockito-all" % "1.10.19" % "test,it",
  "jp.t2v" %% "play2-auth-test" % t2vVersion % "test",
  "org.scalaj" %% "scalaj-http" % "2.3.0" % "test",
  "org.scalacheck" %% "scalacheck" % "1.13.5" % "test",
  "com.github.mkolisnyk" % "cucumber-reports" % "1.0.7" % "test,it",
  "net.masterthought" % "cucumber-reporting" % "3.3.0" % "test,it",
  "net.masterthought" % "cucumber-sandwich" % "3.3.0" % "test,it",
  "com.assertthat" % "selenium-shutterbug" % "0.2" % "test,it"
)
lazy val overrideDependencies = Set(
  "org.seleniumhq.selenium" % "selenium-java" % seleniumVersion % "test,it",
  "org.seleniumhq.selenium" % "selenium-htmlunit-driver" % seleniumVersion % "test,it"
)

lazy val plugins: Seq[Plugins] = Seq(PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin)
lazy val playSettings: Seq[Setting[_]] = Seq.empty
lazy val microservice = Project(appName, file("."))
  .enablePlugins(plugins: _*)
  .settings(
    Concat.groups := Seq(
      "javascripts/apis-app.js" -> group(
        (baseDirectory.value / "app" / "assets" / "javascripts") ** "*.js"
      )
    ),
    UglifyKeys.compressOptions := Seq(
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
    routesGenerator := StaticRoutesGenerator,
    scalaVersion := "2.11.11"
  )
  .settings(playPublishingSettings: _*)
  .settings(inConfig(TemplateTest)(Defaults.testSettings): _*)
  .settings(testOptions in Test := Seq(Tests.Filter(unitFilter))
  )
  .configs(IntegrationTest)
  .settings(inConfig(TemplateItTest)(Defaults.itSettings): _*)
  .settings(
    Keys.fork in IntegrationTest := false,
    unmanagedSourceDirectories in IntegrationTest <<= (baseDirectory in IntegrationTest) (base => Seq(base / "it")),
    unmanagedResourceDirectories in IntegrationTest <<= (baseDirectory in IntegrationTest) (base => Seq(base / "it/resources")),
    addTestReportOption(IntegrationTest, "int-test-reports"),
    testGrouping in IntegrationTest := oneForkedJvmPerTest((definedTests in IntegrationTest).value),
    parallelExecution in IntegrationTest := false)
  .settings(resolvers ++= Seq(Resolver.bintrayRepo("hmrc", "releases"), Resolver.jcenterRepo))
  .configs(ComponentTest)
  .settings(inConfig(ComponentTest)(Defaults.testSettings): _*)
  .settings(
    testOptions in ComponentTest := Seq(Tests.Filter(componentTestFilter)),
    unmanagedSourceDirectories in ComponentTest <<= (baseDirectory in ComponentTest) (base => Seq(base / "test")),
    unmanagedResourceDirectories in ComponentTest <<= (baseDirectory in ComponentTest) (base => Seq(base / "test")),
    unmanagedResourceDirectories in ComponentTest <+= baseDirectory(_ / "target/web/public/test"),
    testOptions in ComponentTest += Tests.Setup(() => System.setProperty("javascript.enabled", "true")),
    testGrouping in ComponentTest := oneForkedJvmPerTest((definedTests in ComponentTest).value),
    parallelExecution in ComponentTest := false
  )
lazy val allPhases = "tt->test;test->test;test->compile;compile->compile"
lazy val allItPhases = "tit->it;it->it;it->compile;compile->compile"
lazy val ComponentTest = config("component") extend Test
lazy val TemplateTest = config("tt") extend Test
lazy val TemplateItTest = config("tit") extend IntegrationTest
lazy val playPublishingSettings: Seq[sbt.Setting[_]] = Seq(

  credentials += SbtCredentials,

  publishArtifact in(Compile, packageDoc) := false,
  publishArtifact in(Compile, packageSrc) := false
) ++
  publishAllArtefacts

def unitFilter(name: String): Boolean = name startsWith "unit"

def componentTestFilter(name: String): Boolean = name startsWith "component.js"

def oneForkedJvmPerTest(tests: Seq[TestDefinition]) =
  tests map { test =>
    Group(
      test.name,
      Seq(test),
      SubProcess(
        ForkOptions(
          runJVMOptions = Seq(
            "-Dtest.name=" + test.name
          )
        )
      )
    )
  }

// Coverage configuration
coverageMinimum := 85
coverageFailOnMinimum := true
coverageExcludedPackages := "<empty>;com.kenshoo.play.metrics.*;.*definition.*;prod.*;app.*;uk.gov.hmrc.BuildInfo"