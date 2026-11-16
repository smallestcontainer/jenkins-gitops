# jenkins-gitops

| Repository | Role | Relation to this repository |
|---|---|---|
| [jenkins-gitops](https://github.com/smallestcontainer/jenkins-gitops) | CI | This repository |
| [url-shortener](https://github.com/smallestcontainer/url-shortener) | Application | The service this Jenkins builds and checks; its `Jenkinsfile` defines the pipeline run here |
| [k3d-gitops](https://github.com/smallestcontainer/k3d-gitops) | CD | Kubernetes manifests that deploy the image built by this Jenkins |

A self-contained Jenkins CI server, defined as code, that builds and checks the
[url-shortener](https://github.com/smallestcontainer/url-shortener) application.

Jenkins is fully configured at startup: plugins, the admin user, credentials, the
GitHub integration and the pipeline job itself are all created from files in this
repository. The setup wizard is disabled, and nothing needs to be configured by hand in the UI.

## Architecture

This repository is the CI part of a GitOps pipeline:

```mermaid
flowchart TD
    dev([Developer]) -->|push to a branch / pull request| repo[GitHub: url-shortener]
    repo -->|webhook| smee[smee.io channel]
    smee -->|"forwarded by the smee service (compose.yaml)"| jenkins["Jenkins multibranch job (pipeline.groovy)"]
    jenkins --> pipeline[Jenkinsfile from url-shortener]
    pipeline -.->|GitHub check on the commit / PR| repo
    pipeline --> branch{release branch?}
    branch -->|no| done([Checks only])
    branch -->|yes: push image| hub[(Docker Hub)]
    branch -->|yes: commit new image tag| cd[GitHub: k3d-gitops]
    cd -->|synced by ArgoCD| k8s[k3d cluster]
    hub -->|pull image| k8s
```

Every push to a branch and every pull request in `url-shortener` triggers the pipeline.
GitHub delivers the webhook through [smee.io](https://smee.io), so Jenkins does not have
to be reachable from the internet. The result is reported back as a GitHub check:
a red check blocks the pull request until the issues are fixed.

Builds of the release branch (set in the `when { branch … }` conditions of the
`Jenkinsfile`) go further: Jenkins pushes the image to Docker Hub, tagged with the short
commit hash, then updates that tag in `kustomization.yaml` of
[k3d-gitops](https://github.com/smallestcontainer/k3d-gitops) with `kustomize edit set image`
and pushes the commit to the branch ArgoCD tracks. ArgoCD picks up the commit and rolls out the new
image to the cluster.

### Repository structure

```
jenkins-gitops/
├── compose.yaml            # Jenkins + smee webhook relay
├── .env                    # Non-secret settings (in .gitignore)
├── .gitignore
└── jenkins/
    ├── .dockerignore       # Keeps secrets/ out of the image build context
    ├── Dockerfile          # Jenkins image with the Docker CLI and bundled plugins
    ├── plugins.txt         # Plugin list with pinned versions, installed at build time
    ├── jenkins.yaml        # Configuration as Code (JCasC): users, credentials, GitHub
    ├── pipeline.groovy     # Job DSL: creates the multibranch pipeline job
    └── secrets/            # Secret files (in .gitignore), see below
```

### Ports and storage

| Setting | Where it is set |
|---|---|
| Web UI and inbound agent ports | `ports` of the Jenkins service in `compose.yaml` |
| Home directory volume (build history and workspaces) | `volumes` in `compose.yaml` |
| Bridge network shared by Jenkins and smee | `networks` in `compose.yaml` |

### Credentials created in Jenkins

| Credential | Type | Source | Used for |
|---|---|---|---|
| GitHub App | GitHub App | `appID` in `jenkins.yaml` + `secrets/github-app-key.pem` | Repository scans, GitHub checks, pushing image tags to `k3d-gitops` |
| Docker Hub | Username / password | `DOCKERHUB_USERNAME` from `.env` + `secrets/dockerhub.token` | Publishing the image |

Credential IDs are set in `jenkins.yaml` and referenced by the `url-shortener` `Jenkinsfile`.

## Installation

### Requirements

- Docker Engine with the Compose plugin
- A GitHub App installed on the `url-shortener` and `k3d-gitops` repositories with
  permissions for contents (read/write: Jenkins pushes image tags to `k3d-gitops`),
  checks (read/write), pull requests (read) and commit statuses
- A Docker Hub account and an access token
- A smee.io channel (open https://smee.io/new and copy the channel ID)

### 1. Clone

```bash
git clone git@github.com:smallestcontainer/jenkins-gitops.git
cd jenkins-gitops
```

### 2. Create `.env`

```dotenv
CASC_JENKINS_CONFIG=/var/jenkins_home/jenkins.yaml

SMEE_TOKEN=<your smee.io channel ID>
JENKINS_URL=http://jenkins:8080

DOCKERHUB_USERNAME=<your Docker Hub username>
```

`JENKINS_URL` must be reachable from the smee container, so use the Jenkins service
name from `compose.yaml`, not `localhost`.

### 3. Create the secret files

```bash
mkdir -p jenkins/secrets

# Jenkins admin login
cat > jenkins/secrets/.env-admin <<'EOF'
JENKINS_ADMIN_USERNAME=admin
JENKINS_ADMIN_PASSWORD=<strong password>
EOF

# GitHub App private key (downloaded from the GitHub App settings page).
# Jenkins requires PKCS#8; convert the key if GitHub gave you PKCS#1:
openssl pkcs8 -topk8 -inform PEM -outform PEM -nocrypt \
  -in <downloaded-key>.pem -out jenkins/secrets/github-app-key.pem

# Docker Hub access token
printf '%s' '<docker hub token>' > jenkins/secrets/dockerhub.token

# `*` skips dotfiles, so .env-admin is listed explicitly
chmod 600 jenkins/secrets/* jenkins/secrets/.env-admin
```

Both `.env` and `jenkins/secrets/` are in `.gitignore`. The secrets reach Jenkins only
as Compose secrets mounted under `/run/secrets/`: `jenkins/.dockerignore` keeps
`secrets/` out of the image build context, and `.env` lies outside it.

### 4. Adjust host-specific values

- **docker group ID.** `group_add` in `compose.yaml` adds the container to the host's
  `docker` group so Jenkins can access the Docker socket. Set it to the GID on your host:
  ```bash
  getent group docker | cut -d: -f3
  ```
  The same GID is set in the Trivy stage of the `url-shortener` `Jenkinsfile`.
- **GitHub App ID.** Set `appID` in `jenkins/jenkins.yaml` to your app's ID.
- **Repository.** Set `repoOwner` and `repository` in `jenkins/pipeline.groovy` to the
  application repository.

### 5. Configure the GitHub webhook

In the webhook settings of the GitHub App (or the repository), set:

- **Payload URL:** `https://smee.io/<SMEE_TOKEN>`
- **Content type:** `application/json`
- **Events:** pull requests, pushes, check runs

### 6. Start

```bash
docker compose up -d --build
```

Open the Jenkins web UI on the port published in `compose.yaml` and log in with the
credentials from `.env-admin`. The multibranch job from `pipeline.groovy` is created
automatically and scans the repository.

## Removal

```bash
docker compose down              # remove containers and network (build history is kept in the volume)
docker compose down -v --rmi local   # also delete the Jenkins home volume and the built Jenkins image
```

Remove the webhook from the GitHub App (or repository) settings and, if no longer
needed, uninstall the GitHub App and revoke the Docker Hub access token.
