#!/usr/bin/env bash
sbt -mem 4000 clean scalastyle coverage "testOnly * -- -l ExcludeFromCoverage" it:test component:test coverageOff "testOnly * -- -n ExcludeFromCoverage" coverageReport
