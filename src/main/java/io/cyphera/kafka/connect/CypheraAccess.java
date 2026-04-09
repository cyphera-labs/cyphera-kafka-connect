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
 * Kafka Connect SMT: Cyphera Access
 *
 * Accesses (decrypts) a protected field using the embedded tag.
 * No policy name needed — the tag identifies the policy.
 *
 * Config:
 *   field.name — the field to access
 *
 * Usage in connector config:
 *   "transforms": "access",
 *   "transforms.access.type": "io.cyphera.kafka.connect.CypheraAccess$Value",
 *   "transforms.access.field.name": "ssn"
 */
public abstract class CypheraAccess<R extends ConnectRecord<R>> implements Transformation<R> {

    private static final String FIELD_CONFIG = "field.name";

    private String fieldName;
    private Cyphera client;

    @Override
    public void configure(Map<String, ?> configs) {
        fieldName = (String) configs.get(FIELD_CONFIG);
        client = CypheraLoader.getInstance();
    }

    @Override
    public R apply(R record) {
        Schema schema = operatingSchema(record);
        Object value = operatingValue(record);

        if (value == null) return record;

        // Schema-less (Map-based)
        if (schema == null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = new HashMap<>((Map<String, Object>) value);
            Object original = map.get(fieldName);
            if (original instanceof String) {
                map.put(fieldName, client.access((String) original));
            }
            return newRecord(record, null, map);
        }

        // Schema-aware (Struct-based)
        Struct struct = (Struct) value;
        Struct updated = new Struct(schema);
        for (Field field : schema.fields()) {
            Object fieldValue = struct.get(field);
            if (field.name().equals(fieldName) && fieldValue instanceof String) {
                updated.put(field.name(), client.access((String) fieldValue));
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
                    ConfigDef.Importance.HIGH, "Name of the field to access (decrypt)");
    }

    @Override
    public void close() {}

    protected abstract Schema operatingSchema(R record);
    protected abstract Object operatingValue(R record);
    protected abstract R newRecord(R record, Schema schema, Object value);

    public static class Key<R extends ConnectRecord<R>> extends CypheraAccess<R> {
        @Override protected Schema operatingSchema(R record) { return record.keySchema(); }
        @Override protected Object operatingValue(R record) { return record.key(); }
        @Override protected R newRecord(R record, Schema schema, Object value) {
            return record.newRecord(record.topic(), record.kafkaPartition(),
                schema, value, record.valueSchema(), record.value(), record.timestamp());
        }
    }

    public static class Value<R extends ConnectRecord<R>> extends CypheraAccess<R> {
        @Override protected Schema operatingSchema(R record) { return record.valueSchema(); }
        @Override protected Object operatingValue(R record) { return record.value(); }
        @Override protected R newRecord(R record, Schema schema, Object value) {
            return record.newRecord(record.topic(), record.kafkaPartition(),
                record.keySchema(), record.key(), schema, value, record.timestamp());
        }
    }
}
