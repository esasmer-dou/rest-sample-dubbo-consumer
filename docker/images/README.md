# Consumer Docker Images

Keep consumer image definitions in this directory. Workspace Dockerfiles are built from the
`rust-spring-performance` root because they install the local `rust-java-rest` and `java-rust-dubbo`
projects before packaging the sample consumer.

| Dockerfile | Build context | Use case |
|------------|---------------|----------|
| `Dockerfile.jlink` | `rest-sample-dubbo-consumer` root | Standalone repo build; requires Maven settings for private GitHub Packages. |
| `Dockerfile.jlink.workspace` | `rust-spring-performance` root | Full consumer image using local workspace modules. |
| `Dockerfile.jlink.native-static.workspace` | `rust-spring-performance` root | Lowest-memory static provider path for no-arg raw JSON reads. |
| `Dockerfile.jlink.full-static.workspace` | `rust-spring-performance` root | Static provider path with typed DTO and command examples. |
| `Dockerfile.jlink.zookeeper.workspace` | `rust-spring-performance` root | ZooKeeper discovery path. |

Examples:

```powershell
docker build -f rest-sample-dubbo-consumer/docker/images/Dockerfile.jlink.native-static.workspace -t rest-sample-dubbo-consumer:native-static-jlink .
docker build --secret id=maven_settings,src=$env:USERPROFILE\.m2\settings.xml -f docker/images/Dockerfile.jlink -t rest-sample-dubbo-consumer:jlink .
```
