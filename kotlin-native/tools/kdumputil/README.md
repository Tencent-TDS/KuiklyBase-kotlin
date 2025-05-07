
# Kotlin Native Heap Dump 工具

### 构建命令

执行以下命令，产物会在 build/libs/ 

```bash
./gradlew :kotlin-native:tools:kdumputil:fatJar  -Pkotlin.native.enabled=true
```