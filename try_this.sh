#!/bin/bash -e

BROWSER=$1
ENVIRONMENT=$2

export SBT_OPTS="-XX:+CMSClassUnloadingEnabled -Xmx4G"

sbt -Dbrowser="${BROWSER:=chrome}" -Denvironment="${ENVIRONMENT:=local}" "component:testOnly uk.gov.hmrc.test.ui.cucumber.runner.Runner" testReport

unset SBT_OPTS
