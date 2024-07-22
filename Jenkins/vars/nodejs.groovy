#!groovy

// import groovy.json.JsonSlurper

def call(nodeHome){

    try{
        ansiColor('xterm'){
            withEnv(["PATH+NODE=${nodeHome}/bin","NODE_HOME=${nodeHome}", 'FORCE_COLOR=1']) {
                configFileProvider([configFile(fileId: 'artifactory-npmrc', targetLocation: '/apps/jenkins/build/.npmrc', variable: 'NPM_CONFIG_USERCONFIG')]){

                    print.print("INFO", "Set NPM config")
                    sh "export FORCE_COLOR=1"

                    print.print("INFO", "Installing dependencies")
                    sh "npm install"

                    print.print("INFO", "Make Build")
                    sh "npm run build"
                }
            }
        }

        currentBuild.result = 'SUCCESS'

    }catch(Exception){
        print.print('ERROR', "Nodejs build failed - ${Exception}")
        currentBuild.result = 'FAILURE'
    }
}
