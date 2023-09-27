#!/bin/bash

sbt -jvm-debug 5005 "run -Drun.mode=Dev -Dhttp.port=9685 $*"
