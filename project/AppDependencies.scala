import sbt._
import play.core.PlayVersion
import play.sbt.PlayImport._

object AppDependencies {
  def apply() : Seq[ModuleID] = compile ++ test

  lazy val cucumberVersion = "6.2.2"
  lazy val seleniumVersion = "2.53.1"
  lazy val enumeratumVersion = "1.5.12"
  lazy val bootstrapVersion = "5.24.0"
  lazy val mongoVersion = "0.68.0"

  val testScope = "test, it, component"

  lazy val compile = Seq(
    ws,
    "uk.gov.hmrc"                 %% "bootstrap-frontend-play-28"     % bootstrapVersion,
    "uk.gov.hmrc.mongo"           %% "hmrc-mongo-play-28"             % mongoVersion,
    "uk.gov.hmrc"                 %% "govuk-template"                 % "5.72.0-play-28",
    "uk.gov.hmrc"                 %% "play-ui"                        % "9.8.0-play-28",
    "uk.gov.hmrc"                 %% "url-builder"                    % "3.5.0-play-28",
    "uk.gov.hmrc"                 %% "play-json-union-formatter"      % "1.15.0-play-28",
    "uk.gov.hmrc"                 %% "http-metrics"                   % "2.5.0-play-28",
    "uk.gov.hmrc"                 %% "json-encryption"                % "4.10.0-play-28",
    "uk.gov.hmrc"                 %% "emailaddress"                   % "3.5.0",
    "uk.gov.hmrc"                 %% "play-conditional-form-mapping"  % "1.10.0-play-28",
    "uk.gov.hmrc"                 %% "play-frontend-hmrc"             % "1.26.0-play-28",
    "commons-net"                 %  "commons-net"                    % "3.6",
    "com.beachape"                %% "enumeratum"                     % enumeratumVersion,
    "com.beachape"                %% "enumeratum-play"                % enumeratumVersion,
    "com.google.zxing"            %  "core"                           % "3.2.1",
    "org.typelevel"               %% "cats-core"                      % "2.6.1",
    "com.typesafe.play"           %% "play-json"                      % "2.9.2",
    "commons-validator"           %  "commons-validator"              % "1.7"
   )

  lazy val test =  Seq(
    "uk.gov.hmrc"                 %% "bootstrap-test-play-28"         % bootstrapVersion,
    "uk.gov.hmrc.mongo"           %% "hmrc-mongo-test-play-28"        % mongoVersion,
    "org.mockito"                 %% "mockito-scala-scalatest"        % "1.16.46",
    "org.jsoup"                   %  "jsoup"                          % "1.13.1",
    "org.scalaj"                  %% "scalaj-http"                    % "2.4.2",
    "com.github.tomakehurst"      %  "wiremock-jre8-standalone"       % "2.31.0",
    "org.scalacheck"              %% "scalacheck"                     % "1.15.4",
    "org.scalatestplus"           %% "scalacheck-1-15"                % "3.2.10.0"
  ).map(_ % testScope) ++
  Seq(
    "io.cucumber"                 %% "cucumber-scala"                 % cucumberVersion,
    "io.cucumber"                 %  "cucumber-junit"                 % cucumberVersion,
    "io.cucumber"                 %  "cucumber-java8"                 % cucumberVersion,
    "org.seleniumhq.selenium"     %  "selenium-java"                  % seleniumVersion,
    "com.assertthat"              %  "selenium-shutterbug"            % "0.2"
  ).map(_ % "component")
}
