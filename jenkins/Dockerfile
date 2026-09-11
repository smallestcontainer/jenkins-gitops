FROM jenkins/jenkins:2.579

ENV JAVA_OPTS='-Djenkins.install.runSetupWizard=false'

WORKDIR /var/jenkins_home

COPY --chown=jenkins:jenkins plugins.txt .

RUN jenkins-plugin-cli -f ./plugins.txt

COPY --chown=jenkins:jenkins pipeline.groovy .

USER jenkins

CMD [ "/usr/bin/tini", "--", "/usr/local/bin/jenkins.sh" ]