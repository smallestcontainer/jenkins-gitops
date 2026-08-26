FROM jenkins/jenkins:2.579

WORKDIR /var/jenkins_home

COPY --chown=jenkins:jenkins plugins.txt .

RUN jenkins-plugin-cli -f ./plugins.txt

USER jenkins

CMD [ "/usr/bin/tini", "--", "/usr/local/bin/jenkins.sh" ]