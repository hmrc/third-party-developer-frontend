#!/bin/bash

sbt "run -Drun.mode=Dev -Dapplication.router=testOnlyDoNotUseInAppConf.Routes -Dhttp.port=9685 $*"
