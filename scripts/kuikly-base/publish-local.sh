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

# chdir to project root.
SCRIPT_DIR=$(cd $(dirname $0) && pwd -P)
ROOT_DIR=$SCRIPT_DIR/../..
cd $ROOT_DIR
echo "cd $ROOT_DIR"

STEP=1
STEP_MESSAGE=""

function stepBegin() {
  STEP_MESSAGE=$1  
  echo ":::: Step $STEP: $STEP_MESSAGE"
}

function stepEnd() {
  okOrExit ":::: Step $STEP: $STEP_MESSAGE - Failed, exiting..."
  ((STEP++))
}

function cleanUp() {
  mv $ROOT_DIR/local.properties.bk $ROOT_DIR/local.properties
}

function okOrExit() {
   if [ $? -ne 0 ]; then
     echo $1
     cleanUp
     exit 1
   fi
}

function readHostArch() {
  # Check if the OS is macOS
  if [[ "$(uname -s)" == "Darwin" ]]; then
      # Check if the architecture is aarch64 (arm64)
      if [[ "$(uname -m)" == "arm64" ]]; then
          ARCH=aarch64
      else
          ARCH=x86_64
      fi
  else
      echo "Not running on macOS， exiting."
      exit 1
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
      -Pkonan.xcodeForSimdOverlay="/Applications/Xcode-15.0.app" \
      -Pkonan.sysrootForSimdOverlay="$SCRIPT_DIR/internal/simdOverlay" \
      "$@"
}

export JDK_18=$(/usr/libexec/java_home -v 1.8)
if [ -z "$JDK_18" ]; then 
  echo "JDK 1.8 is required. Please download and set JDK_18 to the home of JDK 1.8. Exiting."
  exit 1
fi 

readHostArch
DEPLOY_VERSION=2.0.255-SNAPSHOT

[[ -e "$ROOT_DIR/local.properties" ]] && mv $ROOT_DIR/local.properties $ROOT_DIR/local.properties.bk
echo -e "kotlin.build.isObsoleteJdkOverrideEnabled=true\n" >> $ROOT_DIR/local.properties
./gradlew --stop

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

stepBegin "Clean and build Kotlin Native compiler."
rm -Rf ./kotlin-native/dist
GRADLE_NATIVE :kotlin-native:clean :kotlin-native:dist
stepEnd

stepBegin "Build OHOS target."
./gradlew --stop
GRADLE_NATIVE :kotlin-native:ohos_arm64PlatformLibs
stepEnd

stepBegin "Build Other targets and bundle Kotlin Native compiler."
./gradlew --stop
GRADLE_NATIVE :kotlin-native:bundle
stepEnd

stepBegin "Publish Kotlin Native compiler to local."
GRADLE_NATIVE :kotlin-native:publishBundlePrebuiltPublicationToMavenRepository
stepEnd

stepBegin "Clean up."
cleanUp
cd -