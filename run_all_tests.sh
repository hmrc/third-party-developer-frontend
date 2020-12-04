#!/usr/bin/env bash
export SBT_OPTS="-XX:+CMSClassUnloadingEnabled -XX:MaxMetaspaceSize=1G -Xmx1G"
sbt clean scalastyle coverage test it:test component:test coverageReport
python dependencyReport.py
unset SBT_OPTS
