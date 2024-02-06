import sbt._
import play.sbt.PlayImport._

object AppDependencies {
  def apply() : Seq[ModuleID] = compile ++ test

  lazy val cucumberVersion = "7.15.0"
  lazy val seleniumVersion = "4.14.0"
  lazy val bootstrapVersion = "8.4.0"
  lazy val mongoVersion = "1.7.0"
<<<<<<< HEAD
=======
  lazy val commonDomainVersion = "0.11.0"
>>>>>>> 4d8eac24 (APIS-6756 - WIP Play 3.0)
  lazy val apiDomainVersion = "0.13.0"
  lazy val appDomainVersion = "0.34.0"

  val testScope = "test, it, component"

  lazy val compile = Seq(
    ws,
    "uk.gov.hmrc"                 %% "bootstrap-frontend-play-30"             % bootstrapVersion,
    "uk.gov.hmrc"                 %% "play-partials-play-30"                  % "9.1.0",
    "uk.gov.hmrc"                 %% "domain-play-30"                         % "9.0.0",
    "uk.gov.hmrc"                 %% "play-frontend-hmrc-play-30"             % "8.4.0",
    "uk.gov.hmrc.mongo"           %% "hmrc-mongo-play-30"                     % mongoVersion,
    "uk.gov.hmrc"                 %% "crypto-json-play-30"                    % "7.6.0",
    "uk.gov.hmrc"                 %% "http-metrics"                           % "2.8.0",
    "uk.gov.hmrc"                 %% "emailaddress-play-30"                   % "4.0.0",
    "uk.gov.hmrc"                 %% "play-conditional-form-mapping-play-30"  % "2.0.0",
    "commons-net"                 %  "commons-net"                            % "3.6",
    "com.google.zxing"            %  "core"                                   % "3.2.1",
    "commons-validator"           %  "commons-validator"                      % "1.7",
    "uk.gov.hmrc"                 %% "api-platform-api-domain"                % apiDomainVersion,
    "uk.gov.hmrc"                 %% "api-platform-application-domain"        % appDomainVersion
  )

  lazy val test =  Seq(
    "uk.gov.hmrc"                 %% "bootstrap-test-play-30"             % bootstrapVersion,
    "uk.gov.hmrc.mongo"           %% "hmrc-mongo-test-play-30"            % mongoVersion,
    "org.mockito"                 %% "mockito-scala-scalatest"            % "1.17.29",
    // "com.github.tomakehurst"      %  "wiremock-jre8-standalone"           % "2.35.0",
    "org.jsoup"                   %  "jsoup"                              % "1.13.1",
    "org.scalaj"                  %% "scalaj-http"                        % "2.4.2",
    "org.scalacheck"              %% "scalacheck"                         % "1.17.0",
    "org.scalatestplus"           %% "scalacheck-1-17"                    % "3.2.17.0",
    "uk.gov.hmrc"                 %% "api-platform-test-common-domain"    % commonDomainVersion
  ).map(_ % testScope) ++
  Seq(
    "io.cucumber"                 %% "cucumber-scala"                     % "8.20.0",
    "io.cucumber"                 %  "cucumber-junit"                     % cucumberVersion,
    "com.titusfortner"            %  "selenium-logger"                    % "2.3.0",
    "junit"                       %  "junit"                              % "4.13.2",
    "com.novocode"                %  "junit-interface"                    % "0.11",
    "uk.gov.hmrc"                 %% "ui-test-runner"                     % "0.16.0",
    "org.slf4j"                   %  "slf4j-simple"                       % "1.7.36",
  ).map(_ % "component")
}
