#!/groovy

import groovy.json.JsonSlurper
import groovy.xml.*

def call (projectID, buildConfig, branchPrefix) {

    try{
        def artifactory_base = "https://jfrog-artifactory-instance/artifactory/api/storage/"
        def artifactory_url, version, groupId, artifactID
        def pattern = ~/^(\d+\.\d+.$branchPrefix.+\d)$/

        if (fileExists('pom.xml')){
            def pom = new XmlSlurper().parseText(readFile(file: 'pom.xml'))
            version = pom.version.text()
            groupID = pom.groupId.text().replace('.','/')
            artifactID = pom.artifactId.text()
            artifactory_url = artifactory_base + "maven-release/${groupID}/${artifactID}/"
        }else{
            artifactory_url = artifactory_base + "generic-release/${projectID}/${repoName}/${branchName.replace('/','-')}/"
        }

        def requestUrl = new URL(artifactory_url).openConnection()
        if (requestUrl.getResponseCode().equals(200)){
            def request = artifactory_url.toURL().text
            def data = new JsonSlurper().parseText(request)
            List values = []
            data.children.each {
                it.each { k,v ->
                    if ((k == 'uri') && v != '/maven-metadata.xml'){
                        v = v.replace('/','')
                        if (pattern.matcher(v).matches()){ values.add(v) }
                    }
                }
            }

            values = sortedList(values)

            if (values.size() > 0 && ((BUILD_ID.toInteger()) <= (values[-1].tokenize('.')[-1].toInteger()))){
                def message = """
                Higher version '${values[-1]}' already exists in artifactory.
                Triggering next build - ${projectID}/${repoName}/${branchTrigger}/RELEASE/${BUILD_ID.toInteger() + 1}

                Current job for the derived version '${buildConfig[1]}' will be aborted
                """
                print.print('WARN', message)
                build job: "${projectID}/${repoName}/${branchTrigger}/RELEASE", propagate: false, wait: false
                currentBuild.result = 'ABORTED'
            }else{
                currentBuild.result = 'SUCCESS'
            }
        }else{
            print.print('WARN',"${artifactory_url} not found. If this is a new repository please ignore.")
        }

    }catch(Exception){
        print.print('ERROR', "Version Validation failed - ${Exception}")
        currentBuild.result = 'FAILURE'
    }
}

/**
    1. The runtime for the Groovy language is not CPS transformed by jenkins by default
    2. Hence we need to add @NonCPS on top of the method to run sort() in original groovy runtime

    DOCS: https://www.jenkins.io/doc/book/pipeline/cps-method-mismatches/
*/

@NonCPS
def sortedList(values){
    return values.sort(){ it.tokenize('.')[-1] as Integer }
}
