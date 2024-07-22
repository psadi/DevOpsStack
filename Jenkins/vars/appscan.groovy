#!groovy

import groovy.json.JsonSlurper

def call(commitID, fileTypes, type) {
    stage('AppScan') {
        node('onpremlinux7'){
            checkout scm
            unstash repoName
            def slurper = new JsonSlurper().parseText(fileTypes)
            def appScanEnable = true
            try {

                switch(repoName) {
                    case 'cap-csl-portal':
                        def appscan_temp = ".jenkinsvx_tmp/.appscan"
                        for (t in ['java', 'javascript']){
                            appScan {
                                code_sources = "src/"
                                report_type = 'html'
                                include_folder = "src"
                                exclude_folder = "src/test"
                                precompiled_classes = "target/classes"
                                project_type = t
                                report_title = "APPSCAN-${branchName}-${t}"
                                enable = "${appScanEnable}"
                            }

                            def files = findFiles(glob: "${appscan_temp}/*.html")
                            def file_list = []
                            for (f in files) {
                                file_list = "${f.name}"
                            }

                            publishHTML([
                                allowMissing: true,
                                lwaysLinkToLastBuild: false,
                                keepAll: true,
                                includes: '**/*.html,**/*.css,**/*.js,**/*.gif',
                                reportDir: appscan_temp, reportFiles: "${file_list}",
                                reportName: "APPSCAN-${branchName}-${t}",
                                reportTitles: ''
                            ])
                        }
                    break

                    case 'core':
                        def tempSourceDir = ".jenkinsvx_tmp/appscan_src"
                        sh "mkdir -p ${tempSourceDir} && cp -R ENV_HOME/* ${tempSourceDir}/"
                        appScan {
                            version = "${branchName}-${commitID}"
                            code_sources = "${tempSourceDir}/, CUSTO_HOME/"
                            include_folder = "${tempSourceDir}/"
                            report_type = "html"
                            project_type = "plsql"
                            enable = "${appScanEnable}"
                        }
                        sh "rm -rf ${env.WORKSPACE}/.jenkinsvx_tmp"
                    break

                    default:
                        for (f in [slurper.'source'.collate(10), slurper.'target'.collate(10)].transpose()) {
                            appScan {
                                version = "${branchName}-${commitID}"
                                code_sources = f[0].join(', ').replace(' ', '').replace('./', '').trim()
                                precompiled_classes = f[1].join(', ').replace(' ', '').replace('./', '').trim()
                                project_type = type
                                report_type = 'html'
                                report_title = "APPSCAN-${branchName}"
                                enable = "${appScanEnable}"
                            }
                            sh "rm -rf ${env.WORKSPACE}/.jenkinsvx_tmp"
                        }
                }

                currentBuild.result = 'SUCCESS'

            }catch (Exception) {
                print.print('ERROR', "AppScan Failed - ${Exception}")
                currentBuild.result = 'FAILURE'
            }
        }
    }
}
