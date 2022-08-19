#!/usr/bin/env bash
export SBT_OPTS="-XX:+CMSClassUnloadingEnabled -XX:MaxMetaspaceSize=1G -Xmx2G"
sbt clean scalastyle coverage test it:test component:test coverageReport
unset SBT_OPTS
