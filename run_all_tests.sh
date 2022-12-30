#!/usr/bin/env bash
export SBT_OPTS="-XX:+CMSClassUnloadingEnabled -XX:MaxMetaspaceSize=1G -Xmx2G"
sbt clean scalastyle coverage "testOnly * -- -l ExcludeFromCoverage" it:test component:test coverageOff "testOnly * -- -n ExcludeFromCoverage" coverageReport
unset SBT_OPTS
