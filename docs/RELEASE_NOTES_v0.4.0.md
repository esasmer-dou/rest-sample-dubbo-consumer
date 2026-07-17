# rest-sample-dubbo-consumer 0.4.0

`0.4.0` updates the sample to the generated REST and native Dubbo client line.

## What's New

- Uses `rust-java-rest:4.0.0`, `java-rust-dubbo:0.5.0`, and
  `rest-sample-utility:0.3.0`.
- Replaces handwritten Dubbo client wrappers with build-time generated native clients.
- Replaces handwritten startup index files with generated component and route metadata.
- Centralizes measured runtime plans and route budgets in explicit immutable configuration classes.
- Keeps full, native-static, and ZooKeeper profiles as separate build/runtime choices.
- Removes the duplicate `/api/v1/catalog/db/customers` alias. Use the canonical
  `/api/v1/customers/db` route.

## Run

```powershell
mvn clean package
java -jar target/rest-sample-dubbo-consumer-0.4.0.jar
```

REST handlers, validation, response decisions, and service orchestration remain Java code. Rust owns
HTTP I/O and the bounded native Dubbo transport path.
