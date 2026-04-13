package io.cyphera.kafka.connect;

import io.cyphera.Cyphera;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.connect.connector.ConnectRecord;
import org.apache.kafka.connect.data.Field;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.transforms.Transformation;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka Connect SMT: Cyphera Protect
 *
 * Protects (encrypts) a field using format-preserving encryption.
 * Output is tagged — CypheraAccess needs no policy name.
 *
 * Config:
 *   field.name  — the field to protect
 *   policy.name — the Cyphera policy to use (e.g. "ssn")
 *
 * Usage in connector config:
 *   "transforms": "protect",
 *   "transforms.protect.type": "io.cyphera.kafka.connect.CypheraProtect$Value",
 *   "transforms.protect.field.name": "ssn",
 *   "transforms.protect.policy.name": "ssn"
 */
public abstract class CypheraProtect<R extends ConnectRecord<R>> implements Transformation<R> {

    private static final String FIELD_CONFIG = "field.name";
    private static final String POLICY_CONFIG = "policy.name";

    private String fieldName;
    private String policyName;
    private Cyphera client;

    @Override
    public void configure(Map<String, ?> configs) {
        fieldName = (String) configs.get(FIELD_CONFIG);
        policyName = (String) configs.get(POLICY_CONFIG);
        client = CypheraLoader.getInstance();
    }

    @Override
    public R apply(R record) {
        Schema schema = operatingSchema(record);
        Object value = operatingValue(record);

        if (value == null) return record;

        // Raw bytes — parse as JSON map
        if (value instanceof byte[]) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = new java.util.HashMap<>(
                    new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                        (byte[]) value, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}));
                Object original = map.get(fieldName);
                if (original instanceof String) {
                    map.put(fieldName, client.protect((String) original, policyName));
                }
                byte[] result = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(map);
                return newRecord(record, null, result);
            } catch (Exception e) {
                return record; // can't parse, pass through
            }
        }

        // Schema-less (Map-based)
        if (schema == null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = new HashMap<>((Map<String, Object>) value);
            Object original = map.get(fieldName);
            if (original instanceof String) {
                map.put(fieldName, client.protect((String) original, policyName));
            }
            return newRecord(record, null, map);
        }

        // Schema-aware (Struct-based)
        Struct struct = (Struct) value;
        Struct updated = new Struct(schema);
        for (Field field : schema.fields()) {
            Object fieldValue = struct.get(field);
            if (field.name().equals(fieldName) && fieldValue instanceof String) {
                updated.put(field.name(), client.protect((String) fieldValue, policyName));
            } else {
                updated.put(field.name(), fieldValue);
            }
        }
        return newRecord(record, schema, updated);
    }

    @Override
    public ConfigDef config() {
        return new ConfigDef()
            .define(FIELD_CONFIG, ConfigDef.Type.STRING, ConfigDef.NO_DEFAULT_VALUE,
                    ConfigDef.Importance.HIGH, "Name of the field to protect")
            .define(POLICY_CONFIG, ConfigDef.Type.STRING, ConfigDef.NO_DEFAULT_VALUE,
                    ConfigDef.Importance.HIGH, "Cyphera policy name (e.g. ssn, credit_card)");
    }

    @Override
    public void close() {}

    protected abstract Schema operatingSchema(R record);
    protected abstract Object operatingValue(R record);
    protected abstract R newRecord(R record, Schema schema, Object value);

    public static class Key<R extends ConnectRecord<R>> extends CypheraProtect<R> {
        @Override protected Schema operatingSchema(R record) { return record.keySchema(); }
        @Override protected Object operatingValue(R record) { return record.key(); }
        @Override protected R newRecord(R record, Schema schema, Object value) {
            return record.newRecord(record.topic(), record.kafkaPartition(),
                schema, value, record.valueSchema(), record.value(), record.timestamp());
        }
    }

    public static class Value<R extends ConnectRecord<R>> extends CypheraProtect<R> {
        @Override protected Schema operatingSchema(R record) { return record.valueSchema(); }
        @Override protected Object operatingValue(R record) { return record.value(); }
        @Override protected R newRecord(R record, Schema schema, Object value) {
            return record.newRecord(record.topic(), record.kafkaPartition(),
                record.keySchema(), record.key(), schema, value, record.timestamp());
        }
    }
}
