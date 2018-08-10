#!/usr/bin/env bash
sbt "~run -Drun.mode=Stub  -Dhttp.port=9685 $*"

