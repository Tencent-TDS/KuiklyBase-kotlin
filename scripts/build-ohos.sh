#!/bin/zsh

# 
# Tencent is pleased to support the open source community by making TDS-KuiklyBase available.
# Copyright (C) 2025 THL A29 Limited, a Tencent company. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

START_TIME=$(date +%s)

# go to project root.
SCRIPT_DIR=$(cd $(dirname $0) && pwd -P)
ROOT_DIR=$(cd "$SCRIPT_DIR"/../ && pwd -P)
cd "$ROOT_DIR"

# settings
DEPLOY_VERSION=${DEPLOY_VERSION:-2.2.255-SNAPSHOT}

echo ROOT_DIR="$ROOT_DIR"
echo DEPLOY_VERSION="$DEPLOY_VERSION"
env | grep KONAN_DATA_DIR
env | grep GRADLE_USER_HOME

if [ -z "$JDK_18" ] && [[ "$(uname -s)" == "Darwin" ]]; then
  export JDK_18=$(/usr/libexec/java_home -v 1.8)
fi
if [ -z "$JDK_18" ]; then
  echo "JDK 1.8 is required. Install JDK 8, run export JDK_18=<path_to_jdk_8>, then try again. Exiting."
  exit 1
fi

STEP=1
STEP_MESSAGE=""

function stepBegin() {
  STEP_MESSAGE=$1  
  echo ":::: Step $STEP: $STEP_MESSAGE"
}

function stepEnd() {
  ((STEP++))
}

function cleanUp() {
  if [[ -e "$ROOT_DIR/local.properties.bk" ]]; then
    mv $ROOT_DIR/local.properties.bk $ROOT_DIR/local.properties
  fi
}

function readHostArch() {
  if [[ "$(uname -m)" == "arm64" ]]; then
      ARCH=aarch64
  else
      ARCH=x86_64
  fi
  echo "Build on $ARCH."
}

function GRADLE_NATIVE() {
  ./gradlew \
      -PdeployVersion="$DEPLOY_VERSION" \
      -Pversions.kotlin-native="$DEPLOY_VERSION" \
      -PkonanVersion="$DEPLOY_VERSION" \
      -Pbootstrap.kotlin.version="$DEPLOY_VERSION" \
      -Pkotlin.native.enabled=true \
      -Pbootstrap.local=true \
      -Pbootstrap.local.version="$DEPLOY_VERSION" \
      --dependency-verification=off \
      "$@"
}

readHostArch

[[ -e "$ROOT_DIR/local.properties" ]] && mv $ROOT_DIR/local.properties $ROOT_DIR/local.properties.bk
echo -e "kotlin.build.isObsoleteJdkOverrideEnabled=true\n" >> $ROOT_DIR/local.properties
./gradlew --stop

set -ex

stepBegin "Publish boostrap Kotlin libs to local dir: 'build/repo'."
./gradlew publish install -Pkotlin.native.enabled=false -PdeployVersion=$DEPLOY_VERSION -Pversions.kotlin-native=$DEPLOY_VERSION -PkonanVersion=$DEPLOY_VERSION -Pbootstrap.local=false
stepEnd

stepBegin "Build maven part and publish."
$ROOT_DIR/libraries/mvnw -DnewVersion=$DEPLOY_VERSION -DgenerateBackupPoms=false -DprocessAllModules=true -f $ROOT_DIR/libraries/pom.xml versions:set
$ROOT_DIR/libraries/mvnw \
  -f $ROOT_DIR/libraries/pom.xml \
  clean install -DskipTests \
  -Ddeploy-url=file://$ROOT_DIR/build/repo \
  -Ddeploy-snapshot-repo=local \
  -Ddeploy-snapshot-url=file://$ROOT_DIR/build/repo
stepEnd

stepBegin "Clean Kotlin Native dist."
rm -Rf ./kotlin-native/dist
GRADLE_NATIVE :kotlin-native:clean
stepEnd

stepBegin "Bundle Kotlin Native compiler."
./gradlew --stop
GRADLE_NATIVE :kotlin-native:bundle
stepEnd

stepBegin "Publish Kotlin Native compiler to local."
GRADLE_NATIVE :kotlin-native:publishBundlePrebuiltPublicationToMavenRepository
stepEnd

stepBegin "Clean up."
cleanUp
cd -

echo "All build steps SUCCEEDED."

ELAPSED=$(($(date +%s) - START_TIME)); 
echo "Building took: $((ELAPSED/60)) minutes and $((ELAPSED%60)) seconds."
