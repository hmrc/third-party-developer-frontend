#!/bin/bash

sbt "run -Drun.mode=Dev -Dhttp.port=9685 -Ddeskpro-horizon.api-key=${DESKPRO_KEY} $*"
