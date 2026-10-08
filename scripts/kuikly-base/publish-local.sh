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

# Package the Kotlin compiler and Kotlin/Native prebuilt into build/repo for local development.
# Usage:
#   ./publish-local.sh [--targets <filter>]

setopt pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd -P)
ROOT_DIR=$(cd "$SCRIPT_DIR/../.." && pwd -P)
cd "$ROOT_DIR"
echo "cd $ROOT_DIR"

DEPLOY_VERSION="${DEPLOY_VERSION:-2.4.255-SNAPSHOT}"
BUILD_NUMBER="${BUILD_NUMBER:-1}"
KUIKLY_TARGETS="${KUIKLY_TARGETS:-}"
BOOTSTRAP_LOCAL="${BOOTSTRAP_LOCAL:-true}"
KUIKLY_INTERNAL=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --targets)
      KUIKLY_TARGETS="$2"
      shift 2
      ;;
    --targets=*)
      KUIKLY_TARGETS="${1#--targets=}"
      shift
      ;;
    internal)
      KUIKLY_INTERNAL=true
      shift
      ;;
    external)
      KUIKLY_INTERNAL=false
      shift
      ;;
    -h|--help)
      echo "Usage: ./publish-local.sh [--targets <filter>]"
      exit 0
      ;;
    *)
      echo "Unknown argument: $1"
      echo "Usage: ./publish-local.sh [--targets <filter>]"
      exit 1
      ;;
  esac
done

STEP=1
STEP_MESSAGE=""

function step_begin() {
  STEP_MESSAGE=$1
  echo ":::: Step $STEP: $STEP_MESSAGE"
}

function restore_version_files() {
  if ! git -C "$ROOT_DIR" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    return 0
  fi
  local -a files
  files=("libraries/stdlib/src/kotlin/util/KotlinVersion.kt")
  local pom
  while IFS= read -r pom; do
    files+=("$pom")
  done < <(git -C "$ROOT_DIR" ls-files 'libraries' | grep '/pom\.xml$')
  git -C "$ROOT_DIR" checkout HEAD -- "${files[@]}"
}

function clean_up() {
  if [[ -e "$ROOT_DIR/local.properties.bk" ]]; then
    mv "$ROOT_DIR/local.properties.bk" "$ROOT_DIR/local.properties"
  else
    rm -f "$ROOT_DIR/local.properties"
  fi
  restore_version_files
}

function ok_or_exit() {
  local exit_code=$?
  if [[ $exit_code -ne 0 ]]; then
    echo "$1"
    clean_up
    exit "$exit_code"
  fi
}

function step_end() {
  ok_or_exit ":::: Step $STEP: $STEP_MESSAGE - failed, exiting."
  ((STEP++))
}

function gradle_native() {
  local -a opts
  opts=(
    --stacktrace
    -PdeployVersion="$DEPLOY_VERSION"
    -Pbuild.number="$BUILD_NUMBER"
    -Pversions.kotlin-native="$DEPLOY_VERSION"
    -PkonanVersion="$DEPLOY_VERSION"
    -Pkotlin.native.enabled=true
  )
  if [[ "$BOOTSTRAP_LOCAL" == "true" ]]; then
    opts+=(
      -Pbootstrap.local=true
      -Pbootstrap.local.version="$DEPLOY_VERSION"
    )
  else
    opts+=(-Pbootstrap.local=false)
  fi
  if [[ "$KUIKLY_INTERNAL" == "true" ]]; then
    opts+=(-Pkuikly.internal=true)
  fi
  if [[ -n "$KUIKLY_TARGETS" ]]; then
    opts+=(-Pkuikly.targets="$KUIKLY_TARGETS")
  fi
  ./gradlew "${opts[@]}" "$@"
}

if [[ "$(uname -s)" == "Darwin" && -z "${JDK_18:-}" && -z "${JDK_1_8:-}" ]]; then
  export JDK_18=$(/usr/libexec/java_home -v 1.8 2>/dev/null || true)
fi

LOCAL_REPO="file://${ROOT_DIR}/build/repo"

if [[ -e "$ROOT_DIR/local.properties" && ! -e "$ROOT_DIR/local.properties.bk" ]]; then
  mv "$ROOT_DIR/local.properties" "$ROOT_DIR/local.properties.bk"
fi
printf '%s\n' 'kotlin.build.isObsoleteJdkOverrideEnabled=true' > "$ROOT_DIR/local.properties"
./gradlew --stop

step_begin "Set Maven module version to $DEPLOY_VERSION"
"$ROOT_DIR/libraries/mvnw" \
  -DnewVersion="$DEPLOY_VERSION" \
  -DgenerateBackupPoms=false \
  -DprocessAllModules=true \
  -f "$ROOT_DIR/libraries/pom.xml" \
  versions:set
step_end

step_begin "Publish bootstrap Kotlin libs to ${LOCAL_REPO}."
./gradlew --stacktrace \
  publish install \
  -Pkotlin.native.enabled=false \
  -PdeployVersion="$DEPLOY_VERSION" \
  -Pbuild.number="$BUILD_NUMBER" \
  -Pversions.kotlin-native="$DEPLOY_VERSION" \
  -PkonanVersion="$DEPLOY_VERSION" \
  -Pbootstrap.local=false
step_end

step_begin "Build maven part and install to ${LOCAL_REPO}."
"$ROOT_DIR/libraries/mvnw" \
  -f "$ROOT_DIR/libraries/pom.xml" \
  clean install \
  -DskipTests \
  -Ddeploy-url="$LOCAL_REPO" \
  -Ddeploy-snapshot-repo=local \
  -Ddeploy-snapshot-url="$LOCAL_REPO"
step_end

./gradlew --stop
step_begin "Clean, bundle and publish Kotlin/Native to ${LOCAL_REPO}."
rm -rf "$ROOT_DIR/kotlin-native/dist"
gradle_native \
  :kotlin-native:clean \
  :kotlin-native:bundle \
  :kotlin-native:publishBundlePrebuiltPublicationToMavenRepository
step_end

step_begin "Clean up."
clean_up
step_end
echo ":::: Published locally to ${ROOT_DIR}/build/repo."
