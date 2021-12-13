#!/usr/bin/env bash
export SBT_OPTS="-XX:+CMSClassUnloadingEnabled -XX:MaxMetaspaceSize=1G -Xmx1G"
sbt test it:test component:test
unset SBT_OPTS
