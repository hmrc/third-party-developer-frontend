#!/usr/bin/env bash
export SBT_OPTS="-XX:+CMSClassUnloadingEnabled -Xmx4G"
sbt -Dwebdriver.chrome.driver=/usr/bin/chromedriver pre-commit
unset SBT_OPTS
