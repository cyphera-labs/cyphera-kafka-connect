# Cyphera Kafka Connect Demo

Protect sensitive data in real-time as it flows through Kafka using Cyphera SMTs.

## Prerequisites

- Docker and Docker Compose

## Start the Demo

```bash
docker compose up -d
```

Wait ~30s for Kafka and Connect to start. Verify Connect is ready:

```bash
curl -s http://localhost:8083/ | python3 -m json.tool
```

Check that the Cyphera SMTs are loaded:

```bash
curl -s http://localhost:8083/connector-plugins | python3 -m json.tool | grep -i cyphera
```

## Create a Test Topic

```bash
docker exec -it cyphera-kafka-connect-kafka-1 \
  kafka-topics --create --topic test-input --partitions 1 --replication-factor 1 \
  --bootstrap-server kafka:29092
```

## Produce Test Data

```bash
echo '{"id":"1","name":"Alice Johnson","ssn":"123-45-6789","email":"alice@example.com"}
{"id":"2","name":"Bob Smith","ssn":"987-65-4321","email":"bob@example.com"}
{"id":"3","name":"Carol Davis","ssn":"555-12-3456","email":"carol@example.com"}' | \
docker exec -i cyphera-kafka-connect-kafka-1 \
  kafka-console-producer --topic test-input --bootstrap-server kafka:29092
```

## Deploy a Connector with Cyphera SMT

```bash
curl -X POST http://localhost:8083/connectors -H "Content-Type: application/json" -d '{
  "name": "protect-demo",
  "config": {
    "connector.class": "org.apache.kafka.connect.mirror.MirrorSourceConnector",
    "source.cluster.alias": "source",
    "source.cluster.bootstrap.servers": "kafka:29092",
    "target.cluster.bootstrap.servers": "kafka:29092",
    "topics": "test-input",
    "replication.factor": 1,
    "transforms": "protect-ssn",
    "transforms.protect-ssn.type": "io.cyphera.kafka.connect.CypheraProtect$Value",
    "transforms.protect-ssn.field.name": "ssn",
    "transforms.protect-ssn.policy.name": "ssn"
  }
}'
```

## Check the Output

The protected messages appear in the mirrored topic (base64-wrapped by MirrorSource — decode to see JSON):

```bash
docker exec -it cyphera-kafka-connect-kafka-1 \
  kafka-console-consumer --topic source.test-input --from-beginning \
  --max-messages 3 --timeout-ms 10000 --bootstrap-server kafka:29092 2>/dev/null | \
  while read line; do echo "$line" | tr -d '"' | base64 -d; echo; done
```

### Verified Output

```json
{"name":"Alice Johnson","id":"1","email":"alice@example.com","ssn":"T01i6J-xF-07pX"}
{"name":"Bob Smith","id":"2","email":"bob@example.com","ssn":"T01Q1I-cH-Sdcb"}
{"name":"Carol Davis","id":"3","email":"carol@example.com","ssn":"T01b54-Un-4zHt"}
```

SSNs protected with format-preserving encryption. Tags embedded (`T01`). Dashes preserved. Names and emails pass through untouched.

Alice's SSN `123-45-6789` → `T01i6J-xF-07pX` matches the cross-language vector.

## What's Happening

```
test-input topic → MirrorSourceConnector → CypheraProtect SMT → source.test-input topic
```

The `CypheraProtect$Value` SMT intercepts each message, looks up the `ssn` policy, encrypts the `ssn` field with FF1, prepends the tag, and passes it through. Every other field is untouched.

## Cleanup

```bash
docker compose down
```
