# cyphera-kafka-connect

[![CI](https://github.com/cyphera-labs/cyphera-kafka-connect/actions/workflows/ci.yml/badge.svg)](https://github.com/cyphera-labs/cyphera-kafka-connect/actions/workflows/ci.yml)
[![Security](https://github.com/cyphera-labs/cyphera-kafka-connect/actions/workflows/codeql.yml/badge.svg)](https://github.com/cyphera-labs/cyphera-kafka-connect/actions/workflows/codeql.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE)

Format-preserving encryption for [Kafka Connect](https://kafka.apache.org/documentation/#connect) — Single Message Transforms (SMTs) powered by Cyphera.

Built on [`io.cyphera:cyphera`](https://central.sonatype.com/artifact/io.cyphera/cyphera) from Maven Central.

## Quick Start (Demo)

```bash
docker compose up -d
# Wait ~30s for Kafka + Connect to start
```

Kafka Connect REST API at **http://localhost:8083**. See [DEMO.md](DEMO.md) for the full walkthrough.

## SMTs

| SMT | Config | Description |
|-----|--------|-------------|
| `CypheraProtect$Value` | `field.name`, `configuration.name` | Protect a field in message values |
| `CypheraProtect$Key` | `field.name`, `configuration.name` | Protect a field in message keys |
| `CypheraAccess$Value` | `field.name` | Access a field in message values (header-driven) |
| `CypheraAccess$Key` | `field.name` | Access a field in message keys (header-driven) |

## Build

### From source

```bash
mvn package -DskipTests
```

Produces `target/cyphera-kafka-connect-0.1.0.jar` (fat JAR, excludes Kafka Connect API).

### Via Docker

```bash
docker build -t cyphera-kafka-connect .
```

## Install / Deploy

1. Copy the JAR to a directory under Kafka Connect's `plugin.path`:
   ```bash
   mkdir -p /opt/kafka-connect-plugins/cyphera
   cp target/cyphera-kafka-connect-0.1.0.jar /opt/kafka-connect-plugins/cyphera/
   ```
2. Place `cyphera.json` at `/etc/cyphera/cyphera.json` (or set `CYPHERA_CONFIGURATION_FILE`)
3. Restart Kafka Connect workers

## Usage

Add the SMTs to any connector config:

### Protect on source (encrypt as data enters Kafka)

```json
{
  "name": "my-source-connector",
  "config": {
    "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
    "...": "...",
    "transforms": "protect-ssn",
    "transforms.protect-ssn.type": "io.cyphera.kafka.connect.CypheraProtect$Value",
    "transforms.protect-ssn.field.name": "ssn",
    "transforms.protect-ssn.configuration.name": "ssn"
  }
}
```

### Access on sink (decrypt as data leaves Kafka)

```json
{
  "name": "my-sink-connector",
  "config": {
    "connector.class": "org.apache.kafka.connect.file.FileStreamSinkConnector",
    "...": "...",
    "transforms": "access-ssn",
    "transforms.access-ssn.type": "io.cyphera.kafka.connect.CypheraAccess$Value",
    "transforms.access-ssn.field.name": "ssn"
  }
}
```

### Chain multiple transforms

```json
{
  "transforms": "protect-ssn,protect-cc",
  "transforms.protect-ssn.type": "io.cyphera.kafka.connect.CypheraProtect$Value",
  "transforms.protect-ssn.field.name": "ssn",
  "transforms.protect-ssn.configuration.name": "ssn",
  "transforms.protect-cc.type": "io.cyphera.kafka.connect.CypheraProtect$Value",
  "transforms.protect-cc.field.name": "credit_card",
  "transforms.protect-cc.configuration.name": "credit_card"
}
```

## Operations

### Configuration

- Configuration file: `/etc/cyphera/cyphera.json` (or `CYPHERA_CONFIGURATION_FILE` env var)
- Set env var in the Kafka Connect worker config or Docker environment
- Configuration loaded on first transform call — restart Connect workers to reload

### Monitoring

- SMT errors follow Kafka Connect error handling (`errors.tolerance`, `errors.deadletterqueue.topic.name`)
- Check Connect worker logs for `CypheraLoader` entries
- REST API: `GET http://localhost:8083/connectors/{name}/status`

### Upgrading

1. Build a new JAR with the updated SDK version
2. Replace the JAR in the plugin directory
3. Rolling restart Connect workers (zero downtime in distributed mode)

### Troubleshooting

- **Plugin not found** — JAR not in `plugin.path`. Check `GET http://localhost:8083/connector-plugins` for registered transforms.
- **"Unknown configuration"** — configuration file not found or name misspelled. Check `CYPHERA_CONFIGURATION_FILE` on the worker.
- **ClassNotFoundException** — JAR missing or corrupt. Re-copy and restart.

## Configuration File

```json
{
  "configurations": {
    "ssn": { "engine": "ff1", "key_ref": "demo-key", "header": "T01" },
    "credit_card": { "engine": "ff1", "key_ref": "demo-key", "header": "T02" }
  },
  "keys": {
    "demo-key": { "material": "2B7E151628AED2A6ABF7158809CF4F3C" }
  }
}
```

## Future

- Multi-field support (protect multiple fields in one SMT instance)
- Schema Registry / Avro support
- ksqlDB UDF companion (same JAR, registered as ksqlDB functions)
- Confluent Hub listing
- Metrics via JMX (records protected/accessed per second)

## License

Apache 2.0 — Copyright 2026 Horizon Digital Engineering LLC
