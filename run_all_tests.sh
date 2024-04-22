#!/bin/bash -e

BROWSER=$1
ENVIRONMENT=$2

sbt -J-Xmx4G -Dbrowser="${BROWSER:=chrome}" -Denvironment="${ENVIRONMENT:=local}" pre-commit
