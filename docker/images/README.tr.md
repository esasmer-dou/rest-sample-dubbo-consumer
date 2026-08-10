# Consumer Docker Image'ları

[English](README.md) | [Türkçe](README.tr.md)

Consumer image tanımları bu dizinde tutulur. Workspace Dockerfile'ları, local `rust-java-rest` ve
`java-rust-dubbo` projelerini kurduğu için `rust-spring-performance` kökünü build context olarak
kullanır.

| Dockerfile | Build context | Kullanım |
| --- | --- | --- |
| `Dockerfile.jlink` | `rest-sample-dubbo-consumer` kökü | Standalone repo build; private GitHub Packages için Maven settings gerekir |
| `Dockerfile.jlink.workspace` | `rust-spring-performance` kökü | Local workspace modülleriyle full consumer |
| `Dockerfile.jlink.native-static.workspace` | `rust-spring-performance` kökü | No-arg hazır JSON ve static provider için en küçük yol |
| `Dockerfile.jlink.full-static.workspace` | `rust-spring-performance` kökü | Typed DTO ve command içeren static provider yolu |
| `Dockerfile.jlink.zookeeper.workspace` | `rust-spring-performance` kökü | ZooKeeper discovery yolu |

```powershell
docker build `
  -f rest-sample-dubbo-consumer/docker/images/Dockerfile.jlink.native-static.workspace `
  -t rest-sample-dubbo-consumer:native-static-jlink .

docker build `
  --secret id=maven_settings,src=$env:USERPROFILE\.m2\settings.xml `
  -f docker/images/Dockerfile.jlink `
  -t rest-sample-dubbo-consumer:jlink .
```

Tek image biçimi seçin. Yalnız static native discovery gerekiyorsa full veya ZooKeeper image
kullanmayın. Production öncesinde son image'ı gerçek Kubernetes CPU ve memory limitleriyle test edin.
