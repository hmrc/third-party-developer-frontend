#!/usr/bin/env bash
export SBT_OPTS="-XX:+CMSClassUnloadingEnabled -Xmx4G"
sbt clean scalastyle coverage "testOnly * -- -l ExcludeFromCoverage" it:test component:test coverageOff "testOnly * -- -n ExcludeFromCoverage" coverageReport
unset SBT_OPTS
