#!/usr/bin/env bash
sbt clean compile coverage test component:test coverageReport
python dependencyReport.py 
