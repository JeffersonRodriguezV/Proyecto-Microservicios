FROM jenkins/jenkins:lts-jdk17

# 1) Plugins preinstalados (reproducible)
RUN jenkins-plugin-cli --plugins \
    workflow-aggregator docker-workflow pipeline-stage-view git \
    configuration-as-code job-dsl sonar junit coverage \
    cucumber-reports htmlpublisher credentials-binding lockable-resources

# 2) Cliente Docker + plugin compose
USER root
RUN apt-get update && apt-get install -y ca-certificates curl gnupg \
    && install -m 0755 -d /etc/apt/keyrings \
    && curl -fsSL https://download.docker.com/linux/debian/gpg -o /etc/apt/keyrings/docker.asc \
    && chmod a+r /etc/apt/keyrings/docker.asc \
    && echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] \
    https://download.docker.com/linux/debian $(. /etc/os-release && echo $VERSION_CODENAME) stable" \
    > /etc/apt/sources.list.d/docker.list \
    && apt-get update \
    && apt-get install -y docker-ce-cli docker-compose-plugin \
    && rm -rf /var/lib/apt/lists/*

# 3) El usuario jenkins en el grupo docker
RUN groupadd -f docker && usermod -aG docker jenkins
USER jenkins