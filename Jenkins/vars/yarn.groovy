#!groovy

// This Groovy script defines a function named `call` that takes two parameters `nodeHome` and
// `yarnHome`. Inside the function, it attempts to execute a series of steps related to a Yarn build
// process. Here is a breakdown of what the script does:
def call(nodeHome, yarnHome){

    try{
        ansiColor('xterm'){
            withEnv(["PATH+NODE=${nodeHome}/bin","NODE_HOME=${nodeHome}","YARN_HOME=${yarnHome}","PATH+YARN=${yarnHome}/bin","PATH+YARN=${yarnHome}/bin",'FORCE_COLOR=1']) {
                configFileProvider([configFile(fileId: 'artifactory-npmrc', targetLocation: '/apps/jenkins/build/.npmrc', variable: 'NPM_CONFIG_USERCONFIG')]){
                    sh """
                        export FORCE_COLOR=1
                        yarn
                        yarn build
                        ls -lrtha
                    """
                }
            }
        }

        currentBuild.result = 'SUCCESS'

    }catch(Exception){
        print.print('ERROR', "Yarn build failed - ${Exception}")
        currentBuild.result = 'FAILURE'
    }
}
