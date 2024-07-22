# Platform Engineering - DevOps CI/CD Orchestration Stack

This wiki comprises best practices, tools and technical implementations from repository management, branching strategies, build automation, containerization, configuration management and devsecops for a complete CI/CD pipeline.

## Required Tool Stack

| Category | Tool | Description |
| --- | --- | --- |
| **Version Control System** | Git  | Distributed version control system |
| **Build Automation** | Jenkins | Open source automation server |
| **Artifact Repository** | JFrog | Repository manager for build artifacts |
| **Containerization** | Docker | Open platform for developing, shipping, and running applications |
| **Configuration Management** | Ansible | Open-source software provisioning, configuration management, and application-deployment tool |
| **DevSecOps** | SonarQube | Open-source platform for continuous inspection of code quality |
| | Sonatype Nexus vulnerability scanner | Open-source platform for continuous inspection of code quality |
| | Aquascan | Open-source platform for continuous inspection of code quality |

### **Languages & Build System**

| Language | Build System | Description |
| --- | --- | --- |
| **Java** | Maven | Build automation tool used primarily for Java projects |
|| Gradle | |
|| Ant ||
| **Python** | pip | Package installer for Python |
| Javascript| npm | Package installer for Node.js |
|| **Yarn** | Package installer for Node.js (Depends on npm)|


## Jenkins Shared Library Installation & Configuration

Entrypoint for the shared library is the [Jenkins/vars](./Jenkins/vars) directory. The [vars](./Jenkins/vars) directory contains global variables that can be accessed from any pipeline script.

Script to load main library [cicd.groovy](./Jenkins/vars/cicd.groovy)

This shared library may be accessed one of three ways:

1. Add this repo to 'Shared Pipeline Libraries' in the Jenkins UI.
1. Include a `libraries` block in declarative pipeline syntax.
1. Include this library in an `@Library` statement in a Pipeline script.

### Within Pipeline Script

The declarative way to include libraries is the following.

```groovy
libraries {
    lib('SharedLibrary')
}
```

Scripted way to include is the following.

```groovy
@Library('SharedLibrary') _
```

## Project On-boarding

Once the shared library is configured, invoke [cicd.groovy](./Jenkins/vars/cicd.groovy) file to get things started

```groovy
cicd {
    // Options of tools package name/version in artifactory
    javaVersion = 'jdk package'
    nodeVersion =  'nodejs package'
    pythonVersion = 'python package'

    // Choose Build system
    buildSystem = "" // maven, gradle, ant, npm, yarn, pip, liquibase (for db)
    buildArgs == "" // Additional build arguments

    archiveType = "" // zip, tar or N/A
    // to be provided only if archiveType is not N/A
    patterns = "" // file patterns to archive,
    directories == "" // directories to search the patterns to be included into archive
}
```

### Build Versioning

By default the build framework will form versioning based on the following pattern.

|Branch|Format|
|-|-|
|master/main|YY.MM.M.<BUILD_NO>|
|develop|YY.MM.D.<BUILD_NO>|
|release|YY.MM.R.<BUILD_NO>|
|hotfix|<HOTFIX_ID>.<BUILD_NO>|

see [versioning.groovy](./Jenkins/vars/versioning.groovy)

### Code Scanning

Following Quality gate tools are auto included for source code scan (Runs in parallel)

1. JaCoCo
1. JUnit
1. SonarQube
1. AppScan
1. Nexus-LifeCycle-Analysis

If Dockerfile exists in source code then image is automatically built with Aquscanscan performed (for image security)

## Getting involved

Adding libraries to this repo is welcome and encouraged. Should you have
an idea for a useful library or an improvement for an existing one fork
this repo and open a PR.
