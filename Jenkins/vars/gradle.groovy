#!groovy

import groovy.json.*

def call (jdkHome, gradleHome, List buildConfig, buildArgs) {

    try{
        Boolean deploy = buildConfig[0]
        String version = buildConfig[1]
        def args = new JsonSlurper().parseText(buildArgs)

        withEnv(["JAVA_HOME=${jdkHome}", "GRADLE_HOME=${gradleHome}", "PATH+GRADLE=${gradleHome}/bin"]) {
            artifactoryGradle{
                tasks = args."tasks"
                switches = "-Pbuild.version=${buildConfig[1]}"
                deploy_artifacts = "${deploy}".toBoolean()
                deployMavenDescriptors = true
                usesPlugin = true
            }
        }

        currentBuild.result = 'SUCCESS'

    }catch(Exception){
        print.print('ERROR', "Gradle Build Failed - ${Exception}")
        currentBuild.result = 'FAILURE'
    }
}
