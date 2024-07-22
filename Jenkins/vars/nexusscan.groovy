#!groovy

import groovy.json.JsonSlurper

def call (projectID, project_type) {

    def gai

    try {

        switch(project_type) {
            case 'java':
                nexusScan('**/target/*', 'true', 'build', gai.toString())
            break

            case 'javascript':
                nexusScan('**/build/*', 'true', 'build', gai.toString())

            break
        }

        currentBuild.result = 'SUCCESS'

    }catch (Exception) {
        print.print('ERROR', "NexusScan failed - ${Exception}")
        currentBuild.result = 'FAILURE'
    }
}
