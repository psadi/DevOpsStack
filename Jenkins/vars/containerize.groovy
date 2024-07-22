#!groovy

def call (String artifactoryHost, String projectID, String repoName, List buildVersion) {

    try{

        Boolean publish = buildVersion[0]
        def image_version = buildVersion[1]

        ContainerBuild {
            NO_BUILD = 'false'
            DOCKER_IMAGE_NAME = "${artifactoryHost}/${projectID}/${repoName}"
            DOCKER_IMAGE_VERSION = "${image_version}"
            DOCKER_FILE = 'Dockerfile'
            PUBLISH_BUILD = "${publish}".toBoolean()
            SIGN = "false"
            DTA_SCAN = "${publish}".toBoolean()
        }

        currentBuild.result = 'SUCCESS'

    }catch(Exception){
         print.print('ERROR', "Error at containerizing - ${Exception}")
         currentBuild.result = 'FAILURE'
    }

}
