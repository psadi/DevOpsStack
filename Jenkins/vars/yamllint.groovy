#!groovy

def call(){
    docker.withRegistry('https://jfrog-artifactory-instance/', 'jenkins_resolver'){

        dimg = docker.image('ace/py3-yaml:1.1')

        dimg.inside() {
            def validator = libraryResource 'validator.py'
            writeFile(file: 'validator.py', text: validator)
            ansiColor('xterm'){
                def validatorOutput = sh(script:"source /app/venv/bin/activate;python3 validator.py", returnStatus:true, returnStdout:true)
                if (validatorOutput == 1){
                    currentBuild.currentResult == 'FAILURE'
                    throw new RuntimeException("Please validate yaml files")
                }
            }
        }
    }
}
