#!/bin/bash -e

BROWSER=$1
ENVIRONMENT=$2

export SBT_OPTS="-XX:+CMSClassUnloadingEnabled -Xmx4G"

sbt -Dbrowser="${BROWSER:=chrome}" -Denvironment="${ENVIRONMENT:=local}" pre-commit

unset SBT_OPTS
