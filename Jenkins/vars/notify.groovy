#!groovy

/**
 * This method send an email generated with data from Jenkins
 * @param buildStatus String with job result
 * @param emailRecipients Array with emails: emailRecipients = []
 */

import groovy.xml.*
import groovy.json.JsonSlurper

def call (buildConfig, projectID, isDependent, comsType){

    try{
        def message, statusSuccess, hasArtifacts, bankID, userName, version, groupId, artifactID, artifactory_url, image_url, image, extension, cache_api, body
        def artifactory_base = "https://jfrog-artifactory-instance/artifactory"
        def cache_artifactory_base = "https://aws-hk.jfrog-artifactory-instance/artifactory"
        def imagePath = "${artifactory_base}/docker-release/${projectID}/${repoName}/${buildConfig[1]}"
        def jiraUrl
        def cacheArtifact = true
        currentBuild.result = currentBuild.currentResult

        statusSuccess = true
        hasArtifacts = true

        if(currentBuild.result != 'SUCCESS') {
            statusSuccess = false
            hasArtifacts = false
        }

        switch(JOB_BASE_NAME) {
            case 'PR-BUILD':
                message = "PR: ${BUILD_URL} BUILD VERSION: ${buildConfig[1]} is ${currentBuild.result}"
                commentOnPullRequest(message)
                notifyStash()
            break

            case 'RELEASE':
                try {

                    if (env.buildSystem.contains('maven') && env.archiveType == 'N/A'){
                        def pom = new XmlSlurper().parseText(readFile(file: 'pom.xml'))
                        version = pom.version.text()
                        groupID = pom.groupId.text().replace('.','/')
                        artifactID = pom.artifactId.text()
                        artifactory_url = artifactory_base + "/maven-release/${groupID}/${repoName}/${buildConfig[1]}/"
                        if (pom.breadthFirst().any { it.name() == "packaging"}){
                            extension = pom.packaging.text()
                        }else{
                            extension = 'jar'
                        }
                        if (artifactID.toLowerCase().contains('snapshot') || buildConfig[1].toLowerCase().contains('snapshot')){
                            cache_artifactory_url = cache_artifactory_base + "/maven-snapshot/${groupID}/${repoName}/${artifactID}/${repoName}-${artifactID}.${extension}"
                            cache_api = cache_artifactory_base + "/api/storage/maven-snapshot/${groupID}/${repoName}/${artifactID}/${repoName}-${artifactID}.${extension}"
                        }else{
                            cache_artifactory_url = cache_artifactory_base + "/maven-release/${groupID}/${repoName}/${buildConfig[1]}/${repoName}-${buildConfig[1]}.${extension}"
                            cache_api = cache_artifactory_base + "/api/storage/maven-release/${groupID}/${repoName}/${buildConfig[1]}/${repoName}-${buildConfig[1]}.${extension}"
                        }
                    }else if (env.customPackageName != ''){
                            artifactory_url = artifactory_base + "/generic-release/${projectID}/${repoName}/"
                            cache_artifactory_url = cache_artifactory_base + "/generic-release/${projectID}/${repoName}/${branchName.replace('/','-').toLowerCase()}/${buildConfig[1]}/${customPackageName}.${archiveType}"
                            cache_api = cache_artifactory_base + "/api/storage/generic-release/${projectID}/${repoName}/${branchName.replace('/','-').toLowerCase()}/${buildConfig[1]}/${customPackageName}.${archiveType}"
                    }else if (env.buildSystem.contains('gradle')){
                        cacheArtifact = false
                    }else{
                        if (env.archiveType != 'N/A'){
                            artifactory_url = artifactory_base + "/generic-release/${projectID}/${repoName}/"
                            cache_artifactory_url = cache_artifactory_base + "/generic-release/${projectID}/${repoName}/${branchName.replace('/','-').toLowerCase()}/${buildConfig[1]}/${repoName}-${buildConfig[1]}.${archiveType}"
                            cache_api = cache_artifactory_base + "/api/storage/generic-release/${projectID}/${repoName}/${branchName.replace('/','-').toLowerCase()}/${buildConfig[1]}/${repoName}-${buildConfig[1]}.${archiveType}"
                        }
                    }

                    if (fileExists('Dockerfile')){
                        image_url = artifactory_base + "/docker-release/${projectID}/${repoName}/${buildConfig[1]}/"
                        image = "jfrog-artifactory-instance/${projectID}/${repoName}:${buildConfig[1]}"
                    }

                    try{
                        if (cacheArtifact == true){
                            timeout(time: 5, unit: 'MINUTES'){
                                print.print('INFO', "Caching ${cache_artifactory_url} Artifact")
                                print.print('INFO', "This may take some time (Timeout: 10 Minutes)")
                                sh returnStdout: false, script: "curl ${cache_artifactory_url}?trace"
                                sh returnStdout: false, script: "curl ${cache_artifactory_url.replace('aws-hk', 'cache')}?trace"
                            }

                            def artifactValidate = new URL(cache_api).openConnection()
                            def artifactValidateResponse = artifactValidate.getResponseCode()
                            if (artifactValidateResponse.equals(200)){
                                print.print("INFO","Kindly use '${cache_artifactory_url}' for deployments")
                            }else{
                                print.print('WARN', "Error determining cache artifactory url, ${cache_artifactory_url} - Please validate before using this for deployments")
                            }

                        }
                    } catch (Exception){
                        print.print('WARN', "Cache Tracing failed")
                    }

                } catch (Exception){
                    print.print('ERROR', "Notify failed - ${Exception}")
                }
            break

            case 'DEPLOY':
                jiraUrl = "https://jira.org.com/browse/${JIRA_ID}"

                if (!(environment =~/ST|BAU|DEV/)){
                    if (comsType == 'deploymentStart'){
                        body = email_template.call_start(env.JOB_NAME, env.BUILD_URL, statusSuccess, hasArtifacts, artifactory_url, bankID, userName, jiraUrl, isDependent);
                        emailext (
                            to: "email-id",
                            subject: "[DEVOPS-${projectID.toUpperCase()} DEPLOYMENT] ${JIRA_ID.toUpperCase()} IN ${environment.toUpperCase()} FOR ${repoName.toUpperCase()} IS IN-PROGRESS",
                            body: body,
                            mimeType: 'text/html'
                        )
                    }
                }

                if (comsType == 'deploymentEnd'){

                    def comment
                    switch(currentBuild.currentResult) {
                        case 'SUCCESS':
                            transitionJira('deployed')
                            comment = "*{color:GREEN}DEPLOYMENT SUCCESSFUL: {color}* ${BUILD_URL}"
                        break

                        case 'FAILURE':
                            transitionJira('failed')
                            comment = "*{color:RED}DEPLOYMENT FAILED: {color}* ${BUILD_URL}"
                        break
                        case 'ABORTED':
                            transitionJira('failed')
                            comment = "*{color:GREY}DEPLOYMENT ABORTED: {color}* ${BUILD_URL}"
                        break
                    }

                    jiraNotify(comment)
                    if (!(environment =~/ST|BAU|DEV/)){
                       body = email_template.call(env.JOB_NAME, env.BUILD_URL, statusSuccess, hasArtifacts, artifactory_url, bankID, userName, jiraUrl);
                        emailext (
                            to: "EMAIL_ID",
                            subject: "[DEVOPS-${projectID.toUpperCase()} DEPLOYMENT] ${JIRA_ID.toUpperCase()} IN ${environment.toUpperCase()} FOR ${repoName.toUpperCase()} IS ${currentBuild.result}",
                            body: body,
                            mimeType: 'text/html'
                        )
                    }
                }

            break

            case 'OPERATIONS':

                jiraUrl = "https://jira.org.com/browse/${JIRA_ID}"

                if (!(environment =~/ST|BAU|DEV/)){
                    if (comsType == 'operationStart'){
                        body = email_template.call_operations_start(env.JOB_NAME, env.BUILD_URL, statusSuccess, hasArtifacts, artifactory_url, bankID, userName, jiraUrl, isDependent);
                        emailext (
                            to: "EMAIL_ID",
                            subject: "[DEVOPS-${projectID.toUpperCase()} OPERATIONS] ${JIRA_ID.toUpperCase()} AT ${environment.toUpperCase()} IS IN-PROGRESS",
                            body: body,
                            mimeType: 'text/html'
                        )
                    }


                    if (comsType == 'operationEnd'){

                        if (!(environment =~/ST|BAU|DEV/)){
                            body = email_template.call_operations_end(env.JOB_NAME, env.BUILD_URL, statusSuccess, hasArtifacts, artifactory_url, bankID, userName, jiraUrl);
                            emailext (
                                to: "EMAIL_ID",
                                subject: "[DEVOPS-${projectID.toUpperCase()} OPERATIONS] ${JIRA_ID.toUpperCase()} AT ${environment.toUpperCase()} IS ${currentBuild.result}",
                                body: body,
                                mimeType: 'text/html'
                            )
                        }

                        def comment
                        switch(currentBuild.currentResult) {
                            case 'SUCCESS':
                                transitionJira('done')
                                comment = "*{color:GREEN}SERVICE-${action_required.toUpperCase()} SUCCESSFUL: {color}* ${BUILD_URL}"
                            break

                            case 'FAILURE':
                                transitionJira('failed')
                                comment = "*{color:RED}SERVICE-${action_required.toUpperCase()} FAILED: {color}* ${BUILD_URL}"
                            break
                            case 'ABORTED':
                                transitionJira('failed')
                                comment = "*{color:GREY}SERVICE-${action_required.toUpperCase()} ABORTED: {color}* ${BUILD_URL}"
                            break
                        }

                        jiraNotify(comment)
                    }
                }

            break

            case 'START_ON_BOOT':

                try{

                    def healthcheckDir = "${}/reports/"
                    dir(healthcheckDir){

                        if (fileExists('start_on_boot_report.json')){

                            body = email_template.call_boot_start(env.JOB_NAME, env.BUILD_URL, statusSuccess, hasArtifacts, artifactory_url, bankID, userName, jiraUrl);

                        emailext (
                            to: "Toolkit-RCIM@sc.com",
                            subject: "[DEVOPS-${projectID.toUpperCase()} START_ON_BOOT] AT ${environment.toUpperCase()} IS COMPLETED",
                            body: body,
                            mimeType: 'text/html',
                            attachmentsPattern: '*.json'
                        )
                        } else {
                            print.print('WARN','No start on boot report file found')
                        }
                    }

        }catch(Exception){
        print.print('ERROR',"Error sending start on boot report mail ${Exception}")
        }

            break

            case 'HEALTH_CHECK':
                try{
                    def healthcheckDir = "${env.WORKSPACE}/reports/"
                    dir(healthcheckDir){

                        if (fileExists('health_check_report.json')){

                            body = email_template.call_health_check(env.JOB_NAME, env.BUILD_URL, statusSuccess, hasArtifacts, artifactory_url, bankID, userName, jiraUrl);

                        emailext (
                            to: "Toolkit-RCIM@sc.com",
                            subject: "[DEVOPS-${projectID.toUpperCase()} HEALTH_CHECK] AT ${environment.toUpperCase()} IS COMPLETED",
                            body: body,
                            mimeType: 'text/html',
                            attachmentsPattern: '*.json'
                        )
                        } else {
                            print.print('WARN','No health check report file found')
                        }
                    }

                }catch(Exception){
                print.print('ERROR',"Error sending health check report mail ${Exception}")
                }
            break

            case 'USER_UNLOCK':

                body = email_template.call_unlock_user(env.JOB_NAME, env.BUILD_URL, statusSuccess, hasArtifacts, artifactory_url, bankID, userName, jiraUrl);

                emailext (
                    to: "Toolkit-RCIM@sc.com",
                    subject: "[DEVOPS-${projectID.toUpperCase()} UNLOCK USER] IS COMPLETED",
                    body: body,
                    mimeType: 'text/html',
                )

            break

            default:
                print.print('WARNING', "Nothing to do here")
        }

        currentBuild.result = 'SUCCESS'

    }catch(Exception){
        print.print('ERROR', "Notify failed - ${Exception}")
        currentBuild.result = 'FAILURE'
    }
}


def aquaScanNotify(){

    if (branchName.contains('master')){
        try{

        def aquascanDir = "${env.WORKSPACE}/.jenkinsvx_tmp/.aquascan/jfrog-artifactory-instance/wma/${repoName}/"
        dir(aquascanDir){

            if (fileExists('scanout.html')){

            emailext (
                to: "1601688;1623501;1642427;1569412;1411794;1567899;1375285;1516608;1380612;PII_SMs@sc.com;PII_TechLeads@sc.com",
                subject: "[AQUASCAN REPORT] for ${repoName} - ${branchName} #${BUILD_ID}",
                body: "${BUILD_URL}",
                mimeType: 'text/html',
                attachmentsPattern: '*.html'
            )
            } else {
                print.print('WARN','No aquascan report file found')
            }
        }

        }catch(Exception){
        print.print('ERROR',"Error sending aquascan report mail ${Exception}")
        }
    }
}


/**
    APPLICABLE for jiraNotify() and transitionJira() functions

    Jenkins Doc:
    https://www.jenkins.io/doc/pipeline/steps/http_request/#httprequest-perform-an-http-request-and-return-a-response-object
    validResponseCodes : String (optional)
    Configure response code to mark an execution as success.
    You can configure simple code such as "200" or multiple codes separeted by comma(',') e.g. "200,404,500"
    Interval of codes should be in format From:To e.g. "100:399".
    The default (as if empty) is to fail to 4xx and 5xx. That means success from 100 to 399 "100:399".
    To ignore any response code use "100:599".
**/

def jiraNotify(comment){

    if (JIRA_ID.trim() != ''){
        try{
            def jira_body
            jira_body = """{ "body": "${comment}" }"""
            httpRequest httpMode: 'POST',
                        authentication: 'svc_wmpi',
                        contentType: 'APPLICATION_JSON',
                        requestBody: jira_body,
                        url: "https://jira.org.com/rest/api/2/issue/${JIRA_ID}/comment",
                        validResponseCodes: '201'
                        /**
                            Jira Doc:
                            https://docs.atlassian.com/software/jira/docs/api/REST/7.6.1/#api/2/issue-addComment
                            Valid return response:
                                - STATUS 201 Returned if add was successful
                                - STATUS 400 Returned if the input is invalid (e.g. missing required fields, invalid values, and so forth).

                        **/
        } catch(Exception){
            print.print('ERROR', Exception)
            currentBuild.result == 'UNSTABLE' // Capture the Error if the response from jira is anything else and mark the build 'UNSTABLE'
        }
    }
}

def transitionJira(transition){

    if (JIRA_ID.trim() != ''){
        try{
            def resource = libraryResource "handler_transitions.json"
            def resource_json =  readJSON(text: resource)
            def id = resource_json[JOB_BASE_NAME][transition]
            def jira_body = """{ "transition": { "id": "${id}" } }"""
            def request =   httpRequest httpMode: 'POST',
                                        authentication: 'svc_wmpi',
                                        contentType: 'APPLICATION_JSON',
                                        requestBody: jira_body,
                                        url: "https://jira.org.com/rest/api/2/issue/${JIRA_ID}/transitions",
                                        validResponseCodes: '204',
                                        consoleLogResponseBody : true

                                        /**
                                            Jira Doc: https://docs.atlassian.com/software/jira/docs/api/REST/7.6.1/#api/2/issue-doTransition
                                            Responses:
                                                STATUS 400 - If there is no transition specified.
                                                STATUS 204 - Returned if the transition was successful.
                                                STATUS 404 - The issue does not exist or the user does not have permission to view it
                                        **/
        } catch(Exception){
            print.print('ERROR', Exception)
            currentBuild.result == 'UNSTABLE' // Capture the Error if the response from jira is anything else and mark the build 'UNSTABLE'
        }
    }
}
