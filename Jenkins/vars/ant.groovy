#!groovy

// This Groovy script defines a function named `call` that takes three parameters: `jdkHome`,
// `antHome`, and `buildConfig`, which is expected to be a List.
def call (jdkHome, antHome, List buildConfig) {

    try{
        Boolean deploy = buildConfig[0]
        String version = buildConfig[1]
        def appName, buildTimeStamp

        withEnv(["JAVA_HOME=${jdkHome}", "ANT_HOME=${antHome}"]) {

            /*
                override appname and buildtimestamp accordingly
            */

            sh "${ANT_HOME}/bin/ant -buildfile build.xml -DappName=${appName} -DbuildTimeStamp=${buildTimeStamp}"
        }

        currentBuild.result = 'SUCCESS'

        env.customPackageName = "${appName}-${buildTimeStamp}"
        return customPackageName

    }catch(Exception){
        print.print('ERROR', "Ant Build Failed - ${Exception}")
        currentBuild.result = 'FAILURE'
    }
}
