#!groovy

def call (jdkHome, mvnHome, List buildConfig) {

    try{
        Boolean deploy = buildConfig[0]
        String version = buildConfig[1]

        withEnv(["JAVA_HOME=${jdkHome}", "MAVEN_HOME=${mvnHome}", "PATH+MAVEN=${mvnHome}/bin"]) {
            artifactoryMaven {
                pom = 'pom.xml'
                goals = 'clean install -U'
                maven_opts = "--batch-mode -Dbuild.version=${version} -DskipTests=false"
                deploy_artifacts = "${deploy}".toBoolean()
            }
        }

        currentBuild.result = 'SUCCESS'

    }catch(Exception){
        print.print('ERROR', "Maven Build Failed - ${Exception}")
        currentBuild.result = 'FAILURE'
    }
}
