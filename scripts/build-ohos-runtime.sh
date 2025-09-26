SCRIPT_DIR=$(cd $(dirname $0) && pwd -P)
ROOT_DIR=$SCRIPT_DIR/../

set -ex

cd $ROOT_DIR
# package ohos runtime
./gradlew :kotlin-native:runtime:ohos_arm64Runtime -Pbootstrap.local=true -Pkotlin.native.enabled --dependency-verification=off
# remove old runtime in dist folder
rm -rf $ROOT_DIR/kotlin-native/dist/konan/targets/ohos_arm64/native/*
# copy bc from build output to dist
cp $ROOT_DIR/kotlin-native/runtime/build/bitcode/main/ohos_arm64/*.bc $ROOT_DIR/kotlin-native/dist/konan/targets/ohos_arm64/native/
