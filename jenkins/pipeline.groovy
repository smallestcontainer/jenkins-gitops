multibranchPipelineJob('image-jenkins') {
    displayName('Image Jenkins Multibranch')

    branchSources {
        github {
            id('fastapi-app')
            scanCredentialsId('github-app')
            repoOwner('smallestcontainer')
            repository('basic-web-app')
        }
    }

    factory {
        workflowBranchProjectFactory {
            scriptPath('Jenkinsfile')
        }
    }

    orphanedItemStrategy {
        discardOldItems {
            numToKeep(1)
        }
    }
}