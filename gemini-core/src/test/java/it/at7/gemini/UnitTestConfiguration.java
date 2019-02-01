package it.at7.gemini;

import it.at7.gemini.core.*;
import it.at7.gemini.core.Module;
import it.at7.gemini.core.persistence.PersistenceEntityManager;
import it.at7.gemini.core.persistence.PersistenceSchemaManager;
import it.at7.gemini.exceptions.GeminiException;
import it.at7.gemini.schema.Entity;
import it.at7.gemini.schema.EntityField;
import it.at7.gemini.schema.FieldType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

@Configuration
@EnableAutoConfiguration
public class UnitTestConfiguration {

    @Bean
    @Scope("prototype")
    public Transaction transaction() {
        return new Transaction() {
            @Override
            public void open() {

            }

            @Override
            public void close() throws GeminiException {

            }

            @Override
            public void commit() throws GeminiException {

            }

            @Override
            public void rollback() throws GeminiException {

            }
        };
    }

    @Bean
    public PersistenceSchemaManager persistenceSchemaManager() {
        return new PersistenceSchemaManager() {
            @Override
            public void beforeLoadSchema(Map<String, Module> modules, Transaction transaction) throws GeminiException, SQLException, IOException {

            }

            @Override
            public void handleSchemaStorage(Transaction transaction, Collection<Entity> entities) throws GeminiException {

            }

            @Override
            public void deleteUnnecessaryEntites(Collection<Entity> entities, Transaction transaction) throws SQLException {

            }

            @Override
            public void deleteUnnecessaryFields(Entity entity, Set<EntityField> fields, Transaction transaction) throws SQLException {

            }

            @Override
            public void invokeCreateEntityStorageBefore(Entity entity, Transaction transaction) throws SQLException, GeminiException {

            }

            @Override
            public boolean entityStorageExists(Entity entity, Transaction transaction) throws SQLException, GeminiException {
                return true;
            }
        };
    }

    @Bean
    public PersistenceEntityManager persistenceEntityManager() {
        return new PersistenceEntityManager() {

            Map<String, Map<Key, EntityRecord>> store = new HashMap<>();
            Map<String, Long> ids = new HashMap<>();

            class Key {
                Map<String, Object> primitiveKey;

                public Key(Collection<? extends Record.FieldValue> keyFieldValues) {
                    primitiveKey = Record.Converters.toMap(keyFieldValues);
                }

                @Override
                public boolean equals(Object o) {
                    if (this == o) return true;
                    if (o == null || getClass() != o.getClass()) return false;
                    Key key = (Key) o;
                    return Objects.equals(primitiveKey, key.primitiveKey);
                }

                @Override
                public int hashCode() {
                    return Objects.hash(primitiveKey);
                }
            }

            @Override
            public List<EntityRecord> getRecordsMatching(Entity entity, Collection<? extends Record.FieldValue> filterFieldValueType, Transaction transaction) throws GeminiException {
                return List.of();
            }

            @Override
            public Optional<EntityRecord> getRecordByLogicalKey(Entity entity, Collection<? extends Record.FieldValue> logicalKey, Transaction transaction) throws GeminiException {
                return getRecordByLogicalKeyInner(entity, logicalKey);
            }

            @Override
            public Optional<EntityRecord> getRecordByLogicalKey(EntityRecord record, Transaction transaction) throws GeminiException {
                return getRecordByLogicalKeyInner(record.getEntity(), record.getLogicalKeyValue());
            }

            private Optional<EntityRecord> getRecordByLogicalKeyInner(Entity entity, Collection<? extends Record.FieldValue> logicalKey) {
                String entityName = entity.getName().toUpperCase();
                Map<Key, EntityRecord> entityStorage = store.get(entityName);
                if (entityStorage == null) {
                    return Optional.empty();
                }
                Key key = new Key(logicalKey);
                EntityRecord entityRecord = entityStorage.get(key);
                if (entityRecord == null) return Optional.empty();
                for (EntityRecord.EntityFieldValue entityFieldValue : entityRecord.getEntityFieldValues()) {
                    EntityField entityField = entityFieldValue.getEntityField();
                    if (entityField.getType().equals(FieldType.ENTITY_REF)) {
                        Object value = entityFieldValue.getValue();
                        if (value instanceof Number) {
                            Entity entityRef = entityField.getEntityRef();
                            Map<Key, EntityRecord> keyEntityRecordMap = store.get(entityRef.getName().toUpperCase());
                            Optional<EntityRecord> first = keyEntityRecordMap.values().stream()
                                    .filter(r -> r.getIDFieldValueType().getValue().equals(value))
                                    .findFirst();
                            EntityRecord refEntityRecord = first.get();
                            entityRecord.put(entityField, refEntityRecord);
                        }
                    }
                }
                return Optional.ofNullable(entityRecord);
            }


            @Override
            public EntityRecord saveNewEntityRecord(EntityRecord record, Transaction transaction) throws GeminiException {
                Entity entity = record.getEntity();
                String entityName = entity.getName().toUpperCase();
                Map<Key, EntityRecord> entityStorage = store.computeIfAbsent(entityName, k -> new HashMap<>());
                Set<EntityRecord.EntityFieldValue> logicalKeyValue = record.getLogicalKeyValue();
                Key key = new Key(logicalKeyValue);
                EntityRecord existentRecord = entityStorage.get(key);
                if (existentRecord != null) {
                    // error ???
                }
                /* for (EntityRecord.EntityFieldValue entityFieldValue : record.getEntityFieldValues()) {
                    EntityField entityField = entityFieldValue.getEntityField();
                    if (entityField.getType().equals(FieldType.ENTITY_REF)) {
                        Object value = entityFieldValue.getValue();
                        if (value != null) {
                            Entity entityRef = entityField.getEntityRef();
                            EntityReferenceRecord entityReferenceRecord = (EntityReferenceRecord) value;
                            Optional<EntityRecord> recordByLogicalKey = getRecordByLogicalKey(entityRef, entityReferenceRecord.getLogicalKeyRecord(), transaction);
                            EntityRecord entityRefRecord = recordByLogicalKey.get();
                            record.put(entityField.getName().toLowerCase(), entityRefRecord);
                        }
                    }
                } */
                Long lastId = ids.computeIfAbsent(entityName, k -> 0L) + 1;
                ids.put(entityName, lastId);
                record.put(entity.getIdField(), lastId);
                entityStorage.put(key, record);
                return getRecordByLogicalKey(record, transaction).get();
            }

            @Override
            public EntityRecord updateEntityRecord(EntityRecord record, Transaction transaction) throws GeminiException {
                Entity entity = record.getEntity();
                String entityName = entity.getName().toUpperCase();
                Map<Key, EntityRecord> entityStorage = store.get(entityName);
                Optional<Map.Entry<Key, EntityRecord>> first = entityStorage.entrySet().stream()
                        .filter(r -> r.getValue().getIDFieldValueType().getValue().equals(record.getIDFieldValueType().getValue()))
                        .findFirst();
                assert first.isPresent();
                Map.Entry<Key, EntityRecord> entityRecord = first.get();
                Key key = entityRecord.getKey();
                EntityRecord value = entityRecord.getValue();
                Set<EntityRecord.EntityFieldValue> logicalKeyValue = value.getLogicalKeyValue();
                Key newKey = new Key(logicalKeyValue);
                entityStorage.remove(key);
                entityStorage.put(newKey, record);

                List<EntityField> entityReferenceFields = Services.getSchemaManager().getEntityReferenceFields(record.getEntity());
                for (EntityField entityReferenceField : entityReferenceFields) {
                    Entity refEntity = entityReferenceField.getEntity();
                    Map<Key, EntityRecord> entityRefStore = store.get(refEntity.getName().toUpperCase());
                    for (EntityRecord refERec : entityRefStore.values()) {
                        EntityReferenceRecord eref = refERec.get(entityReferenceField);
                        if (eref != null) {
                            if (eref.getLogicalKeyRecord() != null && eref.getLogicalKeyRecord().getFieldValues().equals(EntityRecord.Converters.recordFromJSONMap(entity, key.primitiveKey).getFieldValues())) {
                                refERec.put(entityReferenceField, record);
                            }
                        }
                    }
                }
                return record;
            }

            @Override
            public void deleteEntity(EntityRecord record, Transaction transaction) {
                Entity entity = record.getEntity();
                String entityName = entity.getName().toUpperCase();
                Map<Key, EntityRecord> entityStorage = store.get(entityName);
                Set<EntityRecord.EntityFieldValue> logicalKeyValue = record.getLogicalKeyValue();
                Key key = new Key(logicalKeyValue);
                assert entityStorage.containsKey(key);
                entityStorage.remove(key);
            }

            @Override
            public EntityRecord createOrUpdateEntityRecord(EntityRecord entityRecord, Transaction transaction) {
                return entityRecord;
            }

            @Override
            public int updateEntityRecordsMatchingFilter(Entity entity, Collection<EntityRecord.EntityFieldValue> filterFieldValueType, Collection<EntityRecord.EntityFieldValue> updateWith, Transaction transaction) throws GeminiException {
                int updated = 0;
                Map<Key, EntityRecord> entityStorage = store.get(entity.getName().toUpperCase());
                for (EntityRecord er : entityStorage.values()) {
                    boolean equals = true;
                    for (EntityRecord.EntityFieldValue entityFieldValue : filterFieldValueType) {
                        EntityField entityField = entityFieldValue.getEntityField();
                        Object entityValue = er.get(entityField);
                        Object filterValue = entityFieldValue.getValue();
                        if (!filterValue.equals(entityValue)) {
                            equals = false;
                            continue;
                        }
                    }
                    if (!equals) {
                        for (EntityRecord.EntityFieldValue updateValue : updateWith) {
                            EntityField entityField = updateValue.getEntityField();
                            Object value = updateValue.getValue();
                            if (entityField.getType().equals(FieldType.ENTITY_REF)) {
                                EntityReferenceRecord rR = (EntityReferenceRecord) value;
                                if (rR.hasPrimaryKey() && rR.getPrimaryKey().equals(0L)) {
                                    value = null;
                                }
                            }
                            er.put(updateValue.getEntityField(), value);
                        }
                    }
                }
                return updated;
            }

            @Override
            public List<EntityRecord> getRecordsMatching(Entity entity, FilterRequest filterRequest, Transaction transaction) throws GeminiException {
                String entityName = entity.getName().toUpperCase();
                Map<Key, EntityRecord> entityStorage = store.get(entityName);
                if (entityStorage == null) {
                    return null;
                }
                return new ArrayList<>(entityStorage.values());
            }
        };
    }

}