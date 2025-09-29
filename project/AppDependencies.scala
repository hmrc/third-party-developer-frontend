import sbt._
import play.sbt.PlayImport._

object AppDependencies {
  def apply() : Seq[ModuleID] = compile ++ test

  lazy val seleniumVersion = "4.14.0"
  lazy val bootstrapVersion = "9.19.0"
  lazy val mongoVersion = "2.7.0"
  lazy val apiDomainVersion = "0.20.0"
  lazy val appDomainVersion = "0.87.0"
  lazy val tpdDomainVersion = "0.14.0"
  private val orgDomainVersion = "0.11.0"


  lazy val compile = Seq(
    ws,
    "uk.gov.hmrc"                 %% "bootstrap-frontend-play-30"             % bootstrapVersion,
    "uk.gov.hmrc"                 %% "play-partials-play-30"                  % "10.1.0",
    "uk.gov.hmrc"                 %% "domain-play-30"                         % "11.0.0",
    "uk.gov.hmrc"                 %% "play-frontend-hmrc-play-30"             % "12.13.0",
    "uk.gov.hmrc.mongo"           %% "hmrc-mongo-play-30"                     % mongoVersion,
    "uk.gov.hmrc"                 %% "crypto-json-play-30"                    % "8.3.0",
    "uk.gov.hmrc"                 %% "http-metrics"                           % "2.9.0",
    "uk.gov.hmrc"                 %% "play-conditional-form-mapping-play-30"  % "3.3.0",
    "commons-net"                 %  "commons-net"                            % "3.6",
    "com.google.zxing"            %  "core"                                   % "3.2.1",
    "uk.gov.hmrc"                 %% "api-platform-api-domain"                % apiDomainVersion,
    "uk.gov.hmrc"                 %% "api-platform-application-domain"        % appDomainVersion,
    "uk.gov.hmrc"                 %% "api-platform-tpd-domain"                % tpdDomainVersion,
    "uk.gov.hmrc"                 %% "api-platform-organisation-domain"       % orgDomainVersion
  )

  lazy val test =  Seq(
    "uk.gov.hmrc"                 %% "bootstrap-test-play-30"                   % bootstrapVersion,
    "uk.gov.hmrc.mongo"           %% "hmrc-mongo-test-play-30"                  % mongoVersion,
    "org.mockito"                 %% "mockito-scala-scalatest"                  % "1.17.29",
    "org.jsoup"                   %  "jsoup"                                    % "1.13.1",
    "org.scalacheck"              %% "scalacheck"                               % "1.17.0",
    "org.scalatestplus"           %% "scalacheck-1-17"                          % "3.2.17.0",
    "uk.gov.hmrc"                 %% "api-platform-test-api-domain"             % apiDomainVersion,
    "uk.gov.hmrc"                 %% "api-platform-application-domain-fixtures" % appDomainVersion,
    "uk.gov.hmrc"                 %% "api-platform-test-tpd-domain"             % tpdDomainVersion
  ).map(_ % "test")

  lazy val componentTestDependencies = Seq(
    "io.cucumber"                 %% "cucumber-scala"                     % "8.20.0",
    "io.cucumber"                 %  "cucumber-junit"                     % "7.15.0",
    "com.novocode"                %  "junit-interface"                    % "0.11",
    "uk.gov.hmrc"                 %% "ui-test-runner"                     % "0.45.0",
    "org.slf4j"                   %  "slf4j-simple"                       % "1.7.36"
  ).map(_ % "test")
}
