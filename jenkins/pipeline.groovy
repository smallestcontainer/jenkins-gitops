multibranchPipelineJob('image-jenkins') {
    displayName('Image Jenkins Multibranch')

    branchSources {
        github {
            id('fastapi-app') // Stable, unique identifier
            scanCredentialsId('github-app')
            repoOwner('smallestcontainer')
            repository('basic-web-app')
            apiUri('https://api.github.com') // Must use HTTPS if specified
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

    triggers {
        periodicFolderTrigger {
            interval('5m') // Explicit 5-minute interval
        }
    }
}