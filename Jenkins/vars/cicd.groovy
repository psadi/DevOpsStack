#!groovy

def call(body) {
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def commitID, buildConfig, packageName, project_type, agentLabel
    env.returnVariables = ''
    env.customPackageName = ''

    config.each { key, value ->
        env[key] = value
    }


    pipeline {

        agent any

        environment {
            def projectID = "${BUILD_URL.split('/')[4]}".toLowerCase()
            def branchTrigger = "${BUILD_URL.split('/')[8]}".toLowerCase()
            def artifactoryHost = 'jfrog-artifactory-instance'
            def containerize = false
        }

        stages {
            stage('SetVersion') {
                options {
                    timeout(time: 1, unit: 'MINUTES')
                }

                steps {
                    script {

                        for (c in config){
                            c.each { k,v ->
                                if (!(v.equals('')) || !(v.equals('N/A'))){
                                    print.print('INFO', c)
                                }
                            }
                        }

                        print.print('INFO', "Calling versioning")
                        buildConfig = versioning.call(projectID, '')
                        commitID = sh(returnStdout: true, script:'git rev-parse HEAD').trim()

                        if (fileExists('Dockerfile')){
                          containerize = true
                        }

                        print.print('INFO', "Build version derived  :  ${buildConfig[1]}")
                    }
                }
            }

            stage ('Install Tools') {
                when{
                    expression{
                        currentBuild.result == 'SUCCESS'
                    }
                }
                options {
                    timeout(time: 2, unit: 'MINUTES')
                }

                steps {
                    script {
                        config.each{ key, value ->
                            if (value != 'N/A'){
                                switch(key) {
                                    case 'javaVersion':
                                        jdkHome = installTool value
                                        project_type = 'java'
                                    break

                                    case 'nodeVersion':
                                        nodeHome = installTool value
                                        project_type = 'javascript'
                                    break

                                    case 'buildSystem':
                                        if (value.contains('maven')) { mvnHome = installTool config.buildSystem }
                                        else if (value.contains('ant')) { antHome = installTool config.buildSystem }
                                        else if (value.contains('yarn')){ yarnHome = installTool config.buildSystem }
                                        else if (value.contains('liquibase')) {
                                            liquibaseHome = installTool config.buildSystem
                                            project_type = 'plsql'
                                        }
                                        else if (value.contains('gradle')) { gradleHome = installTool config.buildSystem }
                                    break

                                }
                            }
                        }

                    }
                }
            }

            stage('Build') {
                when{
                    expression{
                        currentBuild.result == 'SUCCESS'
                    }
                }
                options {
                    timeout(time: 60, unit: 'MINUTES')
                }

                steps {
                    script {
                        if (config.buildSystem.contains('maven')) {
                            print.print('INFO', "calling maven.groovy")
                            maven.call(jdkHome, mvnHome, buildConfig)
                        }
                        if (config.buildSystem.contains('ant')) {
                            print.print('INFO', "Calling ant.groovy")
                            packageName = ant.call(jdkHome, antHome, buildConfig)
                        }
                        if (config.buildSystem.contains('npm')) {
                            print.print('INFO', "Calling nodejs.groovy")
                            packageName = nodejs.call(nodeHome)
                        }
                        if (config.buildSystem.contains('yarn')){
                            print.print('INFO', "Calling yarn.groovy")
                            packageName = yarn.call(nodeHome, yarnHome)
                        }
                        if (config.buildSystem.contains('liquibase')){
                            print.print('INFO', "Calling liquibase.groovy")
                            if (JOB_BASE_NAME == 'PR-BUILD'){
                                env.repos = 'all'
                            }
                            packageName = liquibase.call(jdkHome, liquibaseHome, repos, buildConfig)
                        }
                        if (config.buildSystem.contains('gradle')){
                            print.print('INFO', "Calling gradle.groovy")
                            packageName = gradle.call(jdkHome, gradleHome, buildConfig, config.buildArgs)
                        }
                        if (((JOB_BASE_NAME == 'BUILD') || (containerize == true) || ((env.JOB_BASE_NAME == 'RELEASE') &&( RUN_QUALITY_GATE.toBoolean() == true))) && (currentBuild.result == 'SUCCESS')) {
                            print.print('INFO', "Stashing project workspace")
                            stash name: repoName
                        }
                    }

                    script {
                        if (currentBuild.currentResult == 'SUCCESS' && ((config.archiveType != 'N/A') && (env.JOB_BASE_NAME == 'RELEASE'))) {
                            defaultPack = repoName + '-' + buildConfig[1]
                            packageName = "${packageName?:defaultPack}"
                            def packagePatterns = config.patterns.replace(',',' ')

                            sh "mkdir -p ${WORKSPACE}/jenkins_upload"

                            Arrays.asList("${config.directories?:WORKSPACE}".split('\\s*,\\s*')).each {
                                dir(it) {
                                    print.print('INFO', "Calling Archive.groovy, args: '${config.archiveType}' , '${packagePatterns}'")
                                    archive(packageName, config.archiveType, packagePatterns)
                                }
                            }

                            dir('jenkins_upload') {
                                // def filename = packageName + '.' + config.archiveType
                                def targetPath = 'generic-release/' + projectID + '/' + repoName  + '/' + branchTrigger + '/' + buildConfig[1] + '/'
                                uploadToArtifactory {
                                    pattern =  '*'
                                    target = targetPath
                                }
                            }
                        }
                    }
                }
            }

            stage('(BEGIN):Quality Gate') {
                when {
                    expression {
                        ((env.JOB_BASE_NAME == 'BUILD') || ((env.JOB_BASE_NAME == 'RELEASE') &&( RUN_QUALITY_GATE.toBoolean() == true))) &&
                        (!(branchName.contains('feature') || branchName.contains('bugfix'))) &&
                        currentBuild.result == 'SUCCESS'
                    }
                }

                options {
                    timeout(time: 60, unit: 'MINUTES')
                }

                steps {
                    script {
                        if ((env.JOB_BASE_NAME == 'BUILD') || ((env.JOB_BASE_NAME == 'RELEASE') && (RUN_QUALITY_GATE.toBoolean() == true))){
                            fileTypes = getfiles.call()
                            parallel(
                                jacoco: {
                                    if (project_type == 'java'){
                                        print.print('INFO', "Calling jacoco.groovy")
                                        jacoco.call()
                                    }
                                },
                                junit: {
                                    if (project_type == 'java'){
                                        print.print('INFO', "Calling junit.groovy")
                                        junit.call()
                                    }
                                },
                                sonarqube: {
                                    print.print('INFO', "Calling sonarqube.groovy")
                                    sonarqube.call(commitID, fileTypes)
                                },
                                appscan: {
                                    print.print('INFO', "Calling appscan.groovy")
                                    appscan.call(commitID, fileTypes, project_type)
                                },
                                nexusscan: {
                                    print.print('INFO', "Calling nexusscan.groovy")
                                    nexusscan.call(projectID, project_type)
                                }
                            )
                        }
                    }
                }
            }

            stage('(END):Quality Gate') {
                when {
                    expression {
                        env.JOB_BASE_NAME == 'BUILD' && currentBuild.result == 'SUCCESS'
                    }
                }

                steps {
                    script{
                        print.print('INFO', "QUALITY GATE: END, STATUS - ${currentBuild.currentResult}")
                    }
                }
            }

            stage('Containerize') {
                when {
                    expression {
                        containerize == true
                        buildConfig[0] == true
                        env.JOB_BASE_NAME != 'PR-BUILD' && currentBuild.result == 'SUCCESS'
                    }
                }

                agent {
                    label 'notary'
                }

                options {
                    skipDefaultCheckout()
                    timeout(time: 30, unit: 'MINUTES')
                }

                steps {
                    script {
                        if (containerize == true) {
                            cleanWs()
                            unstash repoName
                            print.print('INFO', "Calling containerize.groovy")
                            containerize.call(artifactoryHost, projectID, repoName, buildConfig)
                        }
                    }
                }
            }
        }

        post {
            success {
                script {
                    print.print('INFO', "Calling notify.groovy to perform final actions")
                  	notify.call(buildConfig, projectID, false, 'none')
                    env.returnVariables = buildConfig[1]
                    cleanWs()
                }
            }

            failure {
                script {
                    notify.call(buildConfig, projectID, false, 'none')
                }
            }

            aborted{
                script{
                    error('Current job will be aborted')
                }
            }

            unstable {
                script {
                    notify.call(buildConfig, projectID, false, 'none')
                }
            }
        }
    }
}
