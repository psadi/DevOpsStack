#!groovy

import groovy.json.JsonOutput

def call() {
    List srcFiles = [], targetFiles = [], sureFireFiles = [], jacocoFiles = []

    try {
        def shellOutput = sh(returnStdout: true, script: 'find . -type d -print | grep -v -e ".git" -e "node_modules"').trim()
        for (String i in shellOutput.split('\n')) {
            if ((i.endsWith('/src') || i.endsWith('.jsp') || i.endsWith('.sql'))) {
                srcFiles.add(i)
            }
            else if ((i.endsWith('/target') || i.endsWith('/build'))) {
                targetFiles.add(i)
            }
            else if (i.endsWith('/target/surefire-reports')) {
                sureFireFiles.add(i)
            }
            else if (i.endsWith('/target/site/jacoco')) {
                jacocoFiles.add(i + '/jacoco.xml')
            }
        }

        def data = [
            source:srcFiles,
            target:targetFiles,
            surefire:sureFireFiles,
            jacoco:jacocoFiles
        ]

        println JsonOutput.toJson(data)
        currentBuild.result = 'SUCCESS'
        return JsonOutput.toJson(data)

    } catch (Exception) {
        print.print('ERROR', "Error while scannine for files - ${Exception}")
        currentBuild.result = 'FAILURE'
    }
}
