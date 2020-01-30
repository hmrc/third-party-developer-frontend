#!/usr/bin/env bash
export SBT_OPTS="-XX:+CMSClassUnloadingEnabled -XX:MaxMetaspaceSize=1G"
sbt clean compile test it:test component:test
python dependencyReport.py
unset SBT_OPTS