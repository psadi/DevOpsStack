#!groovy

import java.text.SimpleDateFormat

def call (projectID, build_version) {

    try{
        def date = new Date()
        def sdf = new SimpleDateFormat('yy.M')
        def releaseVersion, snapshotVersion, branchPrefix

        switch (branchName) {
                case 'master':
                branchPrefix = 'M'
                releaseVersion = sdf.format(date) + '.' + branchPrefix + '.' + env.BUILD_ID
                snapshotVersion = '1.0.0-SNAPSHOT'
                break

                case 'develop':
                branchPrefix = 'D'
                releaseVersion = sdf.format(date) + '.' + branchPrefix + '.' + env.BUILD_ID
                snapshotVersion = '2.0.0-SNAPSHOT'
                break

                case ~/hotfix.*/:
                branchPrefix = branchName.split('/')[1] // if branchName is hotfix/WMPI-1234, branchPrefix is WMPI-1234
                releaseVersion =  sdf.format(date) + '.' + branchPrefix + '.' + env.BUILD_ID
                snapshotVersion = branchPrefix + '-SNAPSHOT'
                break

                case ~/bugfix.*/:
                branchPrefix = branchName.split('/')[1]
                releaseVersion =  sdf.format(date) + '.' + branchPrefix + '.' + env.BUILD_ID
                snapshotVersion = branchPrefix + '-SNAPSHOT'
                break

                case ~/feature.*/:
                branchPrefix = branchName.split('/')[1]
                releaseVersion =  sdf.format(date) + '.' + branchPrefix + '.' + env.BUILD_ID
                snapshotVersion = branchPrefix + '-SNAPSHOT'
                break

                case ~/release.*/:
                branchPrefix = branchName.split('/')[1] + '.' + 0
                releaseVersion = branchPrefix + '.' + env.BUILD_ID
                snapshotVersion = branchName.split('/')[1] + '-SNAPSHOT'
                branchPrefix = 0
                break

                default:
                    branchTrigger = branchName
                    releaseVersion = sdf.format(date) + '.' + branchName + '.' + env.BUILD_ID
                    snapshotVersion = '3.0.0-SNAPSHOT'
        }

        List buildConfig = []

        switch (JOB_BASE_NAME) {
            case 'BUILD':
                buildConfig.add(false)
                buildConfig.add(snapshotVersion)
                break

            case 'RELEASE':
                buildConfig.add(true)
                if (build_version == '') {
                    buildConfig.add(releaseVersion)
                } else {
                    buildConfig.add(build_version)
                }
                // version_validator(projectID, buildConfig, branchPrefix)
                break

            case 'PR-BUILD':
                buildConfig.add(false)
                buildConfig.add(snapshotVersion)
                notifyStash()
                break

            default:
                buildConfig.add(false)
                buildConfig.add(releaseVersion)
        }

        currentBuild.result = 'SUCCESS'
        return buildConfig
    }catch(Exception){
        print.print('ERROR', "Version Derivation failed - ${Exception}")
        currentBuild.result = 'FAILURE'
    }
}
