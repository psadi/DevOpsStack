#!groovy

import groovy.json.JsonSlurper

def call(commitID, fileTypes) {
    stage('SonarQube') {
        def slurper = new JsonSlurper().parseText(fileTypes)

        try {
            sonarQube {
                version = "${commitID}"
                code_sources = slurper.'source'.join(', ').replace(' ', '').trim()
                binary_location = slurper.'target'.join(', ').replace(' ', '').trim()
                junit_report_paths = slurper.'surefire'.join(', ').replace(' ', '').trim()
                jacoco_report_paths = slurper.'jacoco'.join(', ').replace(' ', '').trim()
                jacoco_coverage_xml_paths = slurper.'jacoco'.join(', ').replace(' ', '').trim()
            }

            currentBuild.result = 'SUCCESS'

        }catch (Exception) {
            print.print('ERROR', "SonarQube failed - ${Exception}")
            currentBuild.result = 'FAILURE'
        }
    }
}
