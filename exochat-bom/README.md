# exochat-bom

A Gradle `java-platform` that pins dependency versions for the application. It imports the Spring Boot and Spring gRPC BOMs and adds constraints for gRPC, protobuf, protovalidate, common-protos and `libs:proto`. Versions come from `gradle/libs.versions.toml`, so to upgrade, edit the catalog, not this module.

Consumers use `implementation platform(project(':exochat-bom'))`.
