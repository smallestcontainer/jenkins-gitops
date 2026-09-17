multibranchPipelineJob('image-jenkins') {
    displayName('Image Jenkins Multibranch')

    branchSources {
        github {
            id('fastapi-app')
            scanCredentialsId('github-app')
            repository('url-shortener')
            repoOwner('smallestcontainer')
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