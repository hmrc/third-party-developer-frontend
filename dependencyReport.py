#!/usr/bin/python

import os, urllib, json
import requests
import re
import subprocess
from sys import argv

def fetchDependencies(repositoryName):
    r = urllib.urlopen(os.environ['CATALOGUE_DEPENDENCIES_URL'])
    j = json.load(r)
    for i in range(len(j)):
        if j[i]['repositoryName'] == repositoryName:
            return j[i]

    return {}

def findOutOfDateDependencies(dependencies):
    outOfDateDependencies = []
    for i in range(len(dependencies)):
        dependency = dependencies[i]
        latestVersion = (dependency['latestVersion']['major'],
            dependency['latestVersion']['minor'],
            dependency['latestVersion']['patch'])
        currentVersion = (dependency['currentVersion']['major'],
            dependency['currentVersion']['minor'],
            dependency['currentVersion']['patch'])

        if latestVersion > currentVersion:
            outOfDateDependencies.append(dependency)

    return outOfDateDependencies

def reportOnDependencies(dependencyType, dependencies):
    print (dependencyType + " to upgrade:")
    outOfDateDependencies = findOutOfDateDependencies(dependencies)
    if len(outOfDateDependencies) > 0:
        for i in range(len(outOfDateDependencies)):
            dependency = outOfDateDependencies[i]
            print ('    \033[38;5;15m\033[48;5;1m{} {}.{}.{} -> {}.{}.{}\033[39;49m'.format(
                dependency['name'],
                dependency['currentVersion']['major'],
                dependency['currentVersion']['minor'],
                dependency['currentVersion']['patch'],
                dependency['latestVersion']['major'],
                dependency['latestVersion']['minor'],
                dependency['latestVersion']['patch']))
    else:
        print ('\033[38;5;2m    No upgrades required\033[39;49m')


def generateReport(repositoryName):
    print ('Generating dependency report for {}...'.format(repositoryName))
    dependencies = fetchDependencies(repositoryName)

    reportOnDependencies('Libraries', dependencies['libraryDependencies'])
    reportOnDependencies('SBT plugins', dependencies['sbtPluginsDependencies'])
    reportOnDependencies('Other dependencies', dependencies['otherDependencies'])

def getRepoName():
    output = subprocess.check_output(['git', 'remote', '-v'])
    p = re.compile('.*github.com\:hmrc/(.*)\.git.*')
    return p.match(output).group(1)

if __name__ == "__main__":
    if 'CATALOGUE_DEPENDENCIES_URL' in os.environ:
        generateReport(getRepoName())
    else:
        print ('CATALOGUE_DEPENDENCIES_URL environment variable not set - cannot generate dependency report')