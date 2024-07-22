#!groovy

import groovy.xml.*

def call(jdkHome, liquibaseHome, repos, buildConfig){
    def branch
    if (repos == 'all'){
        dir("${env.WORKSPACE}/src/sql/"){
            repos = sh (returnStdout: true, script: "ls | grep -v -e 'template' -e 'README.md' | xargs").replace(' ',',').replace('\n','')
        }
    }
    print.print('INFO', 'Generating changelog')
    generate_changelog(repos, buildConfig)

    if (currentBuild.currentResult == 'SUCCESS'){
        print.print('INFO', 'Validating changelog')
        dir('target'){
            validate_changlog(jdkHome, liquibaseHome)
        }
    }

    if (currentBuild.currentResult == 'SUCCESS'){
        print.print('INFO', 'Uploading package to artifactory')
        dir('target'){
            sh """
                find . -type d -name *@tmp -delete
                zip -r ${repoName}-${buildConfig[1]}.zip * -x '*@tmp'
            """
        }
    }
}

def generate_changelog(repos, buildConfig){

    repos.split(',').each{ repo ->
        def basePath = "${env.WORKSPACE}/src/sql/${repo}"
        def targetPath = "${env.WORKSPACE}/target/${repo}"
        def author = sh script: "git show -s --pretty=\"%an\"", returnStdout: true
            author = author.trim()

        try{
            dir(targetPath){
                ['run', 'rollback'].each{ operation ->

                    def sequenceFile = "${basePath}/${operation}/sequence.lst"
                    def changeLogFile = readFile "${env.WORKSPACE}/src/template/liquibase-changelog.xml"
                    def changeLog = new XmlParser().parseText(changeLogFile)

                    if (fileExists(sequenceFile)){
                        def file = readFile sequenceFile
                        def lines = file.readLines()
                        if (lines.size() > 0){
                            lines.each{ line ->
                                line = line.tokenize('|')
                                if (fileExists("${basePath}/${operation}/${line[0]}")){
                                    switch(line.size()) {
                                        case 1:
                                            newNode = new groovy.util.Node(changeLog, 'changeSet', [author: author, id: new Date().getTime()])
                                        break
                                        case 2:
                                            line[1].split(',').each{ context ->
                                                List approvedContexts = ['pre','post','mandatory']
                                                if (!approvedContexts.contains(context)){
                                                    print.print('ERROR',"Usage of context: '${context}' is not allowed")
                                                    print.print('INFO', "Allowed Values are ${approvedContexts}, Example: sample.sql|pre,post")
                                                    throw new Exception("Job will be 'ABORTED' for above reason")
                                                }
                                            }
                                            newNode = new groovy.util.Node(changeLog, 'changeSet', [author: author, id: new Date().getTime(), context: line[1]])
                                        break
                                        default:
                                            print.print('ERROR', "Issue found: ${basePath}/${operation}/${line[0]}")
                                            throw new Exception("Job will be 'ABORTED' for above reason")
                                    }
                                    newNode.appendNode(("comment"),"operation:'${operation}' by author:'${author}' for sqlFile:'${line[0]}'")
                                    newNode.appendNode("sqlFile", [path:"./${operation}/${line[0]}", relativeToChangelogFile:"true", endDelimiter:"/", encoding:"UTF-8", splitStatements:"true", stripComments:"true"])

                                    dir("${targetPath}/${operation}"){
                                        sh "cp -r ${basePath}/${operation}/${line[0]} ."
                                    }
                                } else {
                                    print.print('ERROR', "${line[0]} file dosent exists, Job will be marked as 'ABORTED'")
                                    throw new Exception("Job will be 'ABORTED' for above reason")
                                    break
                                }
                            }
                        } else {
                            print.print('WARN',"${sequenceFile} is empty")
                            print.print('WARN',"${operation} for ${repo} will be skipped")
                        }
                    } else {
                        print.print('ERROR', "${sequenceFile} dosent exists")
                        throw new Exception("Job will be 'ABORTED' for above reason")
                        break
                    }

                    writeFile(file:"${operation}.xml", text: groovy.xml.XmlUtil.serialize(changeLog), encoding: "UTF-8")
                }
            }

            currentBuild.result = 'SUCCESS'

        }catch(Exception){
            print.print('ERROR',"Failed for exception: ${Exception}")
            print.print('ERROR',"Job will be aborted")
            currentBuild.result = 'ABORTED'
        }
    }
}

def validate_changlog(jdkHome, liquibaseHome){
    try{
        withEnv(["JAVA_HOME=${jdkHome}", "LIQUIBASE_HOME=/apps/jenkins/build/tools", "PATH+WHATEVER=/apps/jenkins/build/tools" ]){
            sh """
                export LIQUIBASE_HOME=/apps/jenkins/build/tools
                PATH=\$PATH:/apps/jenkins/build/tools
                export PATH

                for directory in \$(ls);
                do
                    if [ -d \${directory} ];
                    then
                        liquibase --changelog-file=\${directory}/run.xml checks run;
                        liquibase --changelog-file=\${directory}/rollback.xml checks run;
                    fi;
                done;

                if [ -f 'liquibase.checks-settings.conf' ];
                then
                    rm -f liquibase.checks-settings.conf
                fi;
            """
        }
    }catch(Exception){
        print.print('ERROR',"Failed for exception: ${Exception}")
        print.print('ERROR',"Job will be aborted")
        currentBuild.result = 'ABORTED'
    }
}
