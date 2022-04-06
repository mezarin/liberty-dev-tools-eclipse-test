#!/bin/bash

set -Eeox pipefail

MAVEN_VERSION=3.8.5
MAVEN_SHA=89ab8ece99292476447ef6a6800d9842bbb60787b9b8a45c103aa61d2f205a971d8c3ddfb8b03e514455b4173602bd015e82958c0b3ddc1728a57126f773c743
OPENJ9_VERSION=0.30.1
SEMERU_JDK_VERSION=11.0.14.1
SEMERU_JDK_FIXPACK=1

main() {
    installSoftware
}

installSoftware() {
	sudo apt-get update
	installXDisplayServer
	installJDK
	installMaven
	installDocker
}

installXDisplayServer() {
	sudo apt-get install dbus-x11 at-spi2-core xvfb metacity
}

installJDK() {
    mkdir -p tools/jdk
	local url="https://github.com/ibmruntimes/semeru11-binaries/releases/download/jdk-${SEMERU_JDK_VERSION}%2B${SEMERU_JDK_FIXPACK}_openj9-${OPENJ9_VERSION}/ibm-semeru-open-jdk_x64_linux_${SEMERU_JDK_VERSION}_${SEMERU_JDK_FIXPACK}_openj9-${OPENJ9_VERSION}.tar.gz"
	curl -fsSL -o tools/jdk/semeru-jdk11.tar.gz "$url"
	tar -xzf tools/jdk/semeru-jdk11.tar.gz -C tools/jdk
	export JAVA_HOME="/tools/jdk/jdk-${SEMERU_JDK_VERSION}+${SEMERU_JDK_FIXPACK}"
	PATH="${JAVA_HOME}/bin:${PATH}"
}

installMaven() {
    mkdir -p tools/maven
	local url="https://dlcdn.apache.org/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz"
	curl -fsSL -o tools/maven/apache-maven.tar.gz "$url"
    tar -xzf tools/maven/apache-maven.tar.gz -C tools/maven --strip-components=1
    export MAVEN_HOME="tools/maven/apache-maven-${MAVEN_VERSION}"
    PATH="${MAVEN_HOME}:${PATH}"
}

installDocker() {
    # Remove a previous installation of docker.
	sudo apt-get remove docker docker-engine docker.io containerd runc
	
	# Setup the docker repository before installation.
	sudo apt-get install curl ca-certificates gnupg lsb-release
	curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg
	echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/ubuntu \
         $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
    
    # Install the docker engine.
    sudo apt-get update
    sudo apt-get install docker-ce docker-ce-cli containerd.io
}

main "$@"