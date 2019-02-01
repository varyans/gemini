package it.at7.gemini.core.persistence;

import it.at7.gemini.core.*;
import it.at7.gemini.exceptions.*;
import it.at7.gemini.schema.Entity;
import it.at7.gemini.schema.Field;
import it.at7.gemini.schema.FieldType;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.math.BigInteger;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

import static it.at7.gemini.schema.Field.Converters.logicalKeyFromObject;

@Service
public class PersistenceEntityManagerImpl implements PersistenceEntityManager {

    @Override
    public Optional<EntityRecord> getRecordByLogicalKey(EntityRecord record, Transaction transaction) throws GeminiException {
        Set<EntityRecord.EntityFieldValue> logicalKeyValues = record.getLogicalKeyValue();
        return getRecordByLogicalKeyInner(transaction, record.getEntity(), logicalKeyValues);
    }

    @Override
    public Optional<EntityRecord> getRecordByLogicalKey(Entity entity, Collection<? extends Record.FieldValue> logicalKeyValues, Transaction transaction) throws GeminiException {
        return getRecordByLogicalKeyInner(transaction, entity, logicalKeyValues);
    }

    @Override
    public EntityRecord saveNewEntityRecord(EntityRecord record, Transaction transaction) throws GeminiException {
        TransactionImpl transactionImpl = (TransactionImpl) transaction;
        QueryWithParams queryWithParams = makeInsertQuery(record, transaction);
        try {
            long recordId = transactionImpl.executeInsert(queryWithParams.sql, queryWithParams.params);
            Optional<EntityRecord> insertedRecord = getRecordByLogicalKey(record, transaction);
            checkInsertedRecord(recordId, record, insertedRecord);
            return insertedRecord.get();
        } catch (SQLException e) {
            throw GeminiGenericException.wrap(e);
        }
    }

    @Override
    public EntityRecord updateEntityRecord(EntityRecord record, Transaction transaction) throws GeminiException {
        TransactionImpl transactionImpl = (TransactionImpl) transaction;
        Object id = record.getID();
        if (id == null) {
            throw IdFieldException.ID_FIELD_REQUIRED("update", record);
        }
        try {
            QueryWithParams queryWithParams = makeModifyQuery(record, transaction);
            transactionImpl.executeUpdate(queryWithParams.sql, queryWithParams.params);
            Optional<EntityRecord> recordByLogicalKey = getRecordByLogicalKey(record, transaction);
            assert recordByLogicalKey.isPresent();
            return recordByLogicalKey.get();
        } catch (SQLException e) {
            throw GeminiGenericException.wrap(e);
        }
    }

    @Override
    public void deleteEntity(EntityRecord record, Transaction transaction) throws GeminiException {
        TransactionImpl transactionImpl = (TransactionImpl) transaction;
        Object id = record.getID();
        if (id == null) {
            throw IdFieldException.ID_FIELD_REQUIRED("delete", record);
        }
        try {
            QueryWithParams queryWithParams = makeDeleteQUery(record);
            transactionImpl.executeUpdate(queryWithParams.sql, null);
        } catch (SQLException e) {
            throw GeminiGenericException.wrap(e);
        }
    }

    @Override
    public EntityRecord createOrUpdateEntityRecord(EntityRecord entityRecord, Transaction transaction) throws GeminiException {
        Optional<EntityRecord> recordByLogicalKey = getRecordByLogicalKey(entityRecord, transaction);
        if (recordByLogicalKey.isPresent()) {
            EntityRecord persistedEntityRecord = recordByLogicalKey.get();
            if (!sameOf(entityRecord, persistedEntityRecord, transaction)) {
                Field idField = entityRecord.getEntity().getIdField();
                Object persistedID = persistedEntityRecord.get(idField);
                entityRecord.put(idField, persistedID);
                persistedEntityRecord = updateEntityRecord(entityRecord, transaction);
            }
            return persistedEntityRecord;
        } else {
            return saveNewEntityRecord(entityRecord, transaction);
        }
    }

    @Override
    public List<EntityRecord> getRecordsMatching(Entity entity, Collection<? extends Record.FieldValue> filterFielValue, Transaction transaction) throws GeminiException {
        TransactionImpl transactionImpl = (TransactionImpl) transaction;
        Set<EntityRecord.EntityFieldValue> filter = convertToEntityFieldValues(entity, filterFielValue);
        try {
            QueryWithParams queryWithParams = makeSelectQueryFilteringFiledValue(entity, filter, transaction);
            return transactionImpl.executeQuery(queryWithParams.sql, queryWithParams.params, resultSet -> {
                return fromResultSetToEntityRecord(resultSet, entity, transaction);
            });
        } catch (SQLException e) {
            throw GeminiGenericException.wrap(e);
        }
    }

    @Override
    public int updateEntityRecordsMatchingFilter(Entity entity, Collection<EntityRecord.EntityFieldValue> filterFieldValueType, Collection<EntityRecord.EntityFieldValue> updateWith, Transaction transaction) throws GeminiException {
        TransactionImpl transactionImpl = (TransactionImpl) transaction;
        try {
            QueryWithParams queryWithParams = makeModifyQuery(entity, filterFieldValueType, updateWith, transaction);
            return transactionImpl.executeUpdate(queryWithParams.sql, queryWithParams.params);
        } catch (SQLException e) {
            throw GeminiGenericException.wrap(e);
        }
    }

    @Override
    public List<EntityRecord> getRecordsMatching(Entity entity, FilterRequest filterRequest, Transaction transaction) throws GeminiException {
        TransactionImpl transactionImpl = (TransactionImpl) transaction;
        try {
            QueryWithParams query = createSelectQueryFor(entity);
            addFilterRequestTo(query, filterRequest);
            return transactionImpl.executeQuery(query.sql, query.params, resultSet -> {
                return fromResultSetToEntityRecord(resultSet, entity, transaction);
            });
        } catch (SQLException e) {
            throw GeminiGenericException.wrap(e);
        }
    }

    private String createSelectQuerySQLFor(Entity entity) {
        String entityName = entity.getName().toUpperCase();
        return String.format("SELECT %1$s.* FROM %1$s ", entityName);
    }

    private void addFilterRequestTo(QueryWithParams query, FilterRequest filterRequest) {
        if (!filterRequest.getSearchString().isEmpty()) {
            // need to handle the WHERE
        }
    }

    private QueryWithParams createSelectQueryFor(Entity entity) {
        String select = createSelectQuerySQLFor(entity);
        return new QueryWithParams(select);
    }

    private Set<EntityRecord.EntityFieldValue> convertToEntityFieldValues(Entity entity, Collection<? extends Record.FieldValue> filterFielValueType) throws EntityFieldException {
        Set<EntityRecord.EntityFieldValue> filter = new HashSet<>();
        for (Record.FieldValue fieldValue : filterFielValueType) {
            if (fieldValue instanceof EntityRecord.EntityFieldValue) {
                EntityRecord.EntityFieldValue ef = (EntityRecord.EntityFieldValue) fieldValue;
                filter.add(ef);
            } else {
                filter.add(EntityRecord.EntityFieldValue.create(entity, fieldValue));
            }
        }
        return filter;
    }


    private void checkInsertedRecord(long recordId, EntityRecord record, Optional<EntityRecord> insertedRecord) throws EntityRecordException {
        if (!insertedRecord.isPresent()) {
            throw EntityRecordException.INSERTED_RECORD_NOT_FOUND(record.getEntity(), record.getLogicalKeyValue());
        }
        EntityRecord ins = insertedRecord.get();
        long idField = ins.get(ins.getEntity().getIdField());
        assert recordId == idField;
        // TODO check values is not easy... for Entity Ref and recursive logical keys
        /* for (Record.FieldValue fvt : record.getFieldValues()) {
            Field field = fvt.get();
            Object value = fvt.getValue();
            Object persistedValue = ins.get(field);
            --> here we have some problems with entityReferences assert value.equals(persistedValue);
        } */
    }

    private boolean sameOf(EntityRecord entityRecord, EntityRecord persistedEntityRecord, Transaction transaction) throws GeminiException {
        for (Record.FieldValue fieldValue : entityRecord.getEntityFieldValues()) {
            Field field = fieldValue.getField();
            Object valueRec = fromFieldToPrimitiveValue(fieldValue, transaction);
            Record.FieldValue persistedFVT = persistedEntityRecord.getFieldValue(field);
            Object persistedValue = fromFieldToPrimitiveValue(persistedFVT, transaction);
            if (!((valueRec == null && persistedValue == null) || valueRec.equals(persistedValue))) {
                return false;
            }
        }
        return true;
    }

    private Optional<EntityRecord> getRecordByLogicalKeyInner(Transaction transaction, Entity entity, Collection<? extends Record.FieldValue> logicalKeyValues) throws GeminiException {
        if (logicalKeyValues.isEmpty()) {
            return Optional.empty();
        }
        List<EntityRecord> recordsMatching = getRecordsMatching(entity, logicalKeyValues, transaction);
        int size = recordsMatching.size();
        Assert.isTrue(recordsMatching.isEmpty() || size == 1, String.format("Logical Key must have 0 or 1 records found %s - Database is not consistent with schema", size));
        return size == 0 ? Optional.empty() : Optional.of(recordsMatching.get(0));
    }

    private List<EntityRecord> fromResultSetToEntityRecord(ResultSet rs, Entity entity, Transaction transaction) throws SQLException, GeminiException {
        List<EntityRecord> ret = new ArrayList<>();
        while (rs.next()) {
            EntityRecord er = new EntityRecord(entity);
            for (Field field : entity.getSchemaEntityFields()) {
                FieldType type = field.getType();
                String fieldName = field.getName().toLowerCase();
                if (type.isBasicType()) {
                    Class specificType = typeClass(type);
                    Object object;
                    if(specificType != null) {
                        object = rs.getObject(fieldName, specificType);
                    } else {
                        object = rs.getObject(fieldName);

                    }
                    er.put(field, object);
                }
                if (type.equals(FieldType.ENTITY_REF)) {
                    EntityReferenceRecord entityReferenceRecord = null;
                    Object pkValue = rs.getObject(fieldName);
                    Entity entityRef = field.getEntityRef();
                    if (pkValue != null && (Number.class.isAssignableFrom(pkValue.getClass()) && ((Long) pkValue) != 0)) {
                        entityReferenceRecord = new EntityReferenceRecord(field.getEntityRef());
                        entityReferenceRecord.addPKValue(pkValue);
                        Record.FieldValue fieldValue = EntityRecord.EntityFieldValue.create(entityRef.getIdField(), pkValue);
                        List<EntityRecord> recordsMatching = getRecordsMatching(entityRef, Set.of(fieldValue), transaction);
                        assert recordsMatching.size() == 1;
                        EntityRecord lkEntityRecord = recordsMatching.get(0);
                        for (Field entityLkField : entityRef.getLogicalKey().getLogicalKeyList()) {
                            entityReferenceRecord.addLogicalKeyValue(entityLkField, lkEntityRecord.get(entityLkField));
                        }
                    }
                    er.put(field, entityReferenceRecord);
                }
            }
            er.put(entity.getIdField(), rs.getLong(Field.ID_NAME));
            ret.add(er);
        }
        return ret;
    }

    private QueryWithParams makeInsertQuery(EntityRecord record, Transaction transaction) throws GeminiException {
        Entity entity = record.getEntity();
        String sql = String.format("INSERT INTO %s", entity.getName().toLowerCase());
        Map<String, Object> params = new HashMap<>();
        List<? extends Record.FieldValue> sortedFields = sortFields(record.getEntityFieldValues());
        boolean first = true;
        for (Record.FieldValue field : sortedFields) {
            sql += first ? "(" : ",";
            first = false;
            FieldType type = field.getField().getType();
            if (type.isBasicType() || type.equals(FieldType.ENTITY_REF)) {
                sql += field.getField().getName().toLowerCase();
            }
        }
        sql += ") VALUES ";
        first = true;
        for (Record.FieldValue field : sortedFields) {
            String columnName = field.getField().getName().toLowerCase();
            sql += first ? "(" : ",";
            first = false;
            FieldType type = field.getField().getType();
            if (type.isBasicType() || type.equals(FieldType.ENTITY_REF)) {
                sql += ":" + columnName;
                params.put(columnName, fromFieldToPrimitiveValue(field, transaction));
            }
        }
        sql += ")";
        return new QueryWithParams(sql, params);
    }

    private QueryWithParams makeModifyQuery(EntityRecord record, Transaction transaction) throws GeminiException {
        Entity entity = record.getEntity();
        String sql = String.format("UPDATE %s SET ", entity.getName().toLowerCase());
        Map<String, Object> params = new HashMap<>();
        List<? extends Record.FieldValue> sortedFields = sortFields(record.getEntityFieldValues());
        for (int i = 0; i < sortedFields.size(); i++) {
            Record.FieldValue field = sortedFields.get(i);
            String columnName = field.getField().getName().toLowerCase();
            FieldType type = field.getField().getType();
            if (type.isBasicType() || type.equals(FieldType.ENTITY_REF)) {
                sql += String.format(" %s = :%s", columnName, columnName);
                sql += i == sortedFields.size() - 1 ? " " : " , ";
                params.put(columnName, fromFieldToPrimitiveValue(field, transaction));
            }
        }
        sql += String.format(" WHERE %s = %s", Field.ID_NAME, record.get(record.getEntity().getIdField(), Long.class));
        return new QueryWithParams(sql, params);
    }

    private QueryWithParams makeModifyQuery(Entity entity, Collection<EntityRecord.EntityFieldValue> filterFieldValueType, Collection<EntityRecord.EntityFieldValue> updateWith, Transaction transaction) throws SQLException, GeminiException {
        String sql = String.format("UPDATE %s SET ", entity.getName().toLowerCase());
        Map<String, Object> params = new HashMap<>();
        List<EntityRecord.EntityFieldValue> sortedUpdateWith = sortFields(updateWith);
        for (int i = 0; i < sortedUpdateWith.size(); i++) {
            EntityRecord.EntityFieldValue fieldValueType = sortedUpdateWith.get(i);
            if (!fieldValueType.getEntityField().getEntity().equals(entity)) {
                // TODO error
            }
            String columnName = fieldValueType.getField().getName().toLowerCase();
            FieldType type = fieldValueType.getField().getType();
            if (type.isBasicType() || type.equals(FieldType.ENTITY_REF)) {
                sql += String.format(" %s = :%s", columnName, columnName);
                sql += i == sortedUpdateWith.size() - 1 ? " " : " , ";
                params.put(columnName, fromFieldToPrimitiveValue(fieldValueType, transaction));
            }
        }
        sql += " WHERE ";
        List<EntityRecord.EntityFieldValue> sortedFilterWith = sortFields(filterFieldValueType);
        for (int i = 0; i < sortedFilterWith.size(); i++) {
            EntityRecord.EntityFieldValue fieldValueType = sortedFilterWith.get(i);
            if (!fieldValueType.getEntityField().getEntity().equals(entity)) {
                // TODO error
            }
            String columnName = fieldValueType.getField().getName().toLowerCase();
            String paramName = columnName + "_f";
            FieldType type = fieldValueType.getField().getType();
            if (type.isBasicType() || type.equals(FieldType.ENTITY_REF)) {
                sql += String.format(" %s = :%s", columnName, paramName);
                sql += i == sortedFilterWith.size() - 1 ? " " : " , ";
                params.put(paramName, fromFieldToPrimitiveValue(fieldValueType, transaction));
            }
        }
        return new QueryWithParams(sql, params);
    }

    private QueryWithParams makeSelectQueryFilteringFiledValue(Entity entity, Set<EntityRecord.EntityFieldValue> filterValues, Transaction transaction) throws SQLException, GeminiException {
        String entityName = entity.getName().toLowerCase();
        String sql = String.format("SELECT %1$s.* FROM %1$s WHERE ", entityName);
        Map<String, Object> params = new HashMap<>();
        boolean needAnd = false;
        for (Record.FieldValue filterValue : filterValues) {
            Field field = filterValue.getField();
            FieldType type = field.getType();
            sql += needAnd ? "AND" : "";
            if (type.isBasicType() || type.equals(FieldType.ENTITY_REF)) {
                sql += handleSingleColumnSelectBasicType(entityName, params, filterValue, transaction);
            }
            needAnd = true;
        }
        return new QueryWithParams(sql, params);
    }

    private String handleSingleColumnSelectBasicType(String entityName, Map<String, Object> params, Record.FieldValue fieldValue, Transaction transaction) throws SQLException, GeminiException {
        String colName = fieldValue.getField().getName();
        Object value = fromFieldToPrimitiveValue(fieldValue, transaction);
        String res = String.format(" %s.%s = :%2$s ", entityName, colName);
        params.put(colName, value);
        return res;
    }

    private QueryWithParams makeDeleteQUery(EntityRecord record) {
        Entity entity = record.getEntity();
        String sql = String.format("DELETE FROM %s WHERE %s = %s", entity.getName().toLowerCase(), Field.ID_NAME, record.get(record.getEntity().getIdField(), Long.class));
        return new QueryWithParams(sql, null);
    }


    private Object fromFieldToPrimitiveValue(Record.FieldValue fieldValue, Transaction transaction) throws GeminiException {
        Object value = fieldValue.getValue();
        Field field = fieldValue.getField();
        FieldType type = field.getType();
        if (value == null) {
            return handleNullValueForField(type);
        }
        if (type.isBasicType()) {
            return value;
        }
        if (type.equals(FieldType.ENTITY_REF)) {
            EntityReferenceRecord refRecord;
            if (!EntityReferenceRecord.class.isAssignableFrom(value.getClass())) {
                refRecord = logicalKeyFromObject(field.getEntityRef(), value);
            } else {
                refRecord = (EntityReferenceRecord) value;
            }
            if (refRecord.hasPrimaryKey()) {
                return refRecord.getPrimaryKey();
            } else {
                assert refRecord.hasLogicalKey();
                Record lkValue = refRecord.getLogicalKeyRecord();
                Entity entityRef = field.getEntityRef();
                Set<Record.FieldValue> lkFieldValuesType = lkValue.getFieldValues(entityRef.getLogicalKey().getLogicalKeySet());

                List<EntityRecord> lkRecords = getRecordsMatching(entityRef, lkFieldValuesType, transaction);
                if (lkRecords.isEmpty()) {
                    throw EntityRecordException.LK_NOTFOUND(entityRef, lkFieldValuesType);
                }
                if (lkRecords.size() != 1) {
                    throw EntityRecordException.MULTIPLE_LK_FOUND(refRecord);
                }
                EntityRecord entityRecord = lkRecords.get(0);
                return entityRecord.get(entityRef.getIdField());
            }
        }
        // TODO complex types
        return null;
    }

    private Object handleNullValueForField(FieldType type) {
        switch (type) {
            case NUMBER:
                return 0;
            case PK:
                return 0;
            case TEXT:
                return "";
            case LONG:
                return 0L;
            case DOUBLE:
                return 0.;
            case BOOL:
                return false;
            case TIME:
            case DATE:
            case DATETIME:
                return null;
            case ENTITY_REF:
                return 0;
            case RECORD:
                break;
        }
        return "";
    }

    private Class typeClass(FieldType type) {
        switch (type) {
            case NUMBER:
                return null;
            case PK:
                return BigInteger.class;
            case TEXT:
                return String.class;
            case LONG:
                return Long.class;
            case DOUBLE:
                return Double.class;
            case BOOL:
                return Boolean.class;
            case TIME:
                return LocalTime.class;
            case DATE:
                return LocalDate.class;
            case DATETIME:
                return LocalDateTime.class;
            case ENTITY_REF:
                return BigInteger.class;
            case RECORD:
                break;
        }
        throw new RuntimeException(String.format("No Class for type %s", type.name()));
    }

    private <T extends Record.FieldValue> List<T> sortFields(Collection<T> fieldValue) {
        return fieldValue.stream().sorted(Comparator.comparing(f -> f.getField().getName())).collect(Collectors.toList());
    }

    class QueryWithParams {
        String sql;
        Map<String, Object> params;

        public QueryWithParams(String sql) {
            this.sql = sql;
            this.params = new HashMap<>();
        }

        public QueryWithParams(String sql, Map<String, Object> params) {
            this.sql = sql;
            this.params = params;
        }
    }
}