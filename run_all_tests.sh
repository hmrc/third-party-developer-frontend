#!/usr/bin/env bash
export SBT_OPTS="-XX:+CMSClassUnloadingEnabled -Xmx4G"
sbt pre-commit
unset SBT_OPTS
