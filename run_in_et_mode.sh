#!/bin/bash

sbt "~run -Drun.mode=Stub  -Dhttp.port=9685 -DisExternalTestEnvironment=true $*"
