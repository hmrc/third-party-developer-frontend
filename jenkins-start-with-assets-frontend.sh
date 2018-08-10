#!/bin/bash

echo "Starting ASSETS"

cd $WORKSPACE
rm -rf service-manager-config
git clone git@github.com:HMRC/service-manager-config.git

sm --stop ALL
sm --cleanlogs
sm --start ASSETS_FRONTEND -f --wait 60 --noprogress

echo "Running tests for Third Party Developer Frontend"

cd $WORKSPACE

echo "Start tests and component tests..."

sbt clean coverage test component:test coverageOff coverageReport dist-tgz publish

echo "Gracefully shutdown server..."

sm --stop ALL
