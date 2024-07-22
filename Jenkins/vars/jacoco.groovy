#!groovy

def call() {
    try {
        step([$class: 'JacocoPublisher', execPattern:'**/**.exec', classPattern: '**/classes', sourcePattern: '**/src/main/java', exclusionPattern: 'src/test*'])
        currentBuild.result = 'SUCCESS'
    }catch (Exception) {
        print.print('ERROR', "Jacoco Publisher Failure - ${Exception}")
        currentBuild.result = 'FAILURE'
    }
}
