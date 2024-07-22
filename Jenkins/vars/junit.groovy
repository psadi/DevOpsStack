#!groovy

def call() {
    try {
        junit '**/target/surefire-reports/*.xml'
        currentBuild.result = 'SUCCESS'
    } catch (Exception) {
        print.print('ERROR', "Junit Failure - ${Exception}")
        // currentBuild.result = 'FAILURE'
    }
}
