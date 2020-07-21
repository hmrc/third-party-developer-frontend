/*
 * Copyright 2020 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package js

import java.io._

import steps.Env
import org.junit.runner.RunWith
import org.junit.{AfterClass, BeforeClass}

import io.cucumber.junit.{Cucumber, CucumberOptions}
import io.cucumber.junit.CucumberOptions.SnippetType.CAMELCASE

@RunWith(classOf[Cucumber])
  @CucumberOptions(
    features = Array("features"),
    glue = Array("steps"),
    dryRun= false,
    snippets= CAMELCASE,
    strict = true,
    plugin = Array("pretty",
      "html:target/component-reports/cucumber",
      "json:target/component-reports/cucumber.json"
      ),
     tags = "not @wip and not @skip"
  )
class FeatureSuite

object FeatureSuite {
  import scala.collection.JavaConverters._

  @BeforeClass
  def beforeCukesRun() = Env.startServer()

  @AfterClass
  def afterCukesRun() {
    Env.shutdown()
//     reporting()
  }
//   def reporting() {
//     Runtime.getRuntime().addShutdownHook(new Thread() {
//       @Override
//       override def run() {
//         val cucumberDetail = new CucumberDetailedResults()
//         cucumberDetail.setOutputDirectory("target/")
//         cucumberDetail.setOutputName("cucumber-results/cucumber-non-sandbox-results")
//         cucumberDetail.setSourceFile("target/component-reports/cucumber.json")
//         cucumberDetail.execute(true, Array[String]("PDF"))
//         val resultsOverview = new CucumberResultsOverview()
//         resultsOverview.setOutputDirectory("target/")
//         resultsOverview.setOutputName("cucumber-results/cucumber-non-sandbox-results")
//         resultsOverview.setSourceFile("target/component-reports/cucumber.json")
//         resultsOverview.execute()
//         reportingNetMasterThought()
//       }
//     })
//   }

  def reportingNetMasterThought() {
    val reportOutputDirectory = new File("target/cucumber-results/cucumber-non-sandbox-results")
//    val jenkinsBasePath : String= ""
    val buildNumber : String = "1"
    val projectName :String= "Third-party-developer-frontend"
//    val skippedFails = true
//    val pendingFails = false
//    val undefinedFails = true
//    val missingFails = true
//    val runWithJenkins = false
//    val parallelTesting = false
    val jsonReportList = List("target/component-reports/cucumber.json")
    val configuration = new net.masterthought.cucumber.Configuration(reportOutputDirectory, projectName)
    // TODO: Check these are defaulted to false
//    configuration.setParallelTesting(parallelTesting)
//    configuration.setRunWithJenkins(runWithJenkins)
    configuration.setBuildNumber(buildNumber)
    new net.masterthought.cucumber.ReportBuilder(jsonReportList.asJava, configuration).generateReports()
  }
}
