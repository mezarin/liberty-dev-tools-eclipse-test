#!/bin/bash

set -Ex

# Current time.
currentTime=(date +"%Y/%m/%d-%H:%M:%S:%3N")

main() {
    echo -e "\n> $(${currentTime[@]}): Build: Building the plugin"
	ls -lR /tools/jdk
	echo "@ed maven"
	ls -lR /tools/maven
    export DISPLAY=:99.0
    Xvfb -ac :99 -screen 0 1280x1024x16 > /dev/null 2>&1 &
    metacity --sm-disable --replace 2> metacity.err &
    mvn clean install
    
    rc=$?
    if [ rc -ne 0 ]; then
        echo "ERROR: Failure while driving mvn install on plugin. rc: ${rc}."
        echo "DEBUG: Liberty messages.log:\n"
        cat tests/applications/maven/liberty-maven-test-app/target/liberty/wlp/usr/servers/defaultServer/logs/messages.log
        echo "DEBUG: Environment variables:\n"
        env
    fi
}

main "$@"