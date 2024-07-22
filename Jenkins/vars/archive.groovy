#!groovy

def call(filename, extension, packagePatterns) {

    try{
        switch (extension) {
            case 'zip':
                sh """
                    echo ${filename}.${extension}
                    if [ ! -f ${filename}.${extension} ]; then
                        zip -r ${filename}.${extension} ${packagePatterns}
                    fi
                """
                break

            case ~/.*tar.*/:
                sh """
                    if [ ! -f ${filename}.${extension} ]; then
                        tar -zcvf ${filename}.${extension} ${packagePatterns}
                    fi
                """
                break

            default:
                print.print('ERROR', 'Unsupported archival format.')
        }

        if (extension != ''){
            sh """
                if [ ! -f ${WORKSPACE}/jenkins_upload/${filename}.${extension} ]; then
                    mv ${filename}.${extension} ${WORKSPACE}/jenkins_upload/.
                fi
            """
        } else {
            sh """
                if [ ! -f ${WORKSPACE}/jenkins_upload/${filename} ]; then
                    mv ${filename} ${WORKSPACE}/jenkins_upload/.
                fi
            """
        }

        currentBuild.result = 'SUCCESS'

    }catch(Exception){
        print.print('ERROR', "Archiving Failed - ${Exception}")
        currentBuild.result = 'FAILURE'
    }
}
