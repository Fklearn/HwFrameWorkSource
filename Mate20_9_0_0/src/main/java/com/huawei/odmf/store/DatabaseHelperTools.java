package com.huawei.odmf.store;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteException;
import com.huawei.nb.coordinator.helper.BusinessTypeEnum;
import com.huawei.odmf.core.DatabaseQueryService;
import com.huawei.odmf.database.DataBase;
import com.huawei.odmf.exception.ODMFIllegalArgumentException;
import com.huawei.odmf.exception.ODMFRuntimeException;
import com.huawei.odmf.model.AEntityId;
import com.huawei.odmf.model.api.Attribute;
import com.huawei.odmf.model.api.Entity;
import com.huawei.odmf.model.api.EntityId;
import com.huawei.odmf.model.api.Index;
import com.huawei.odmf.model.api.ObjectModel;
import com.huawei.odmf.model.api.Relationship;
import com.huawei.odmf.utils.JudgeUtils;
import com.huawei.odmf.utils.LOG;
import com.huawei.odmf.utils.SqlUtil;
import com.huawei.odmf.utils.ThreadLocalDateUtil;
import java.util.ArrayList;
import java.util.List;

class DatabaseHelperTools {
    private static final String METADATA_COLUMN_NAME = "name";
    private static final String METADATA_COLUMN_VERSION = "version";
    private static final String METADATA_DATABASE_VERSION = "databaseVersion";
    private static final String METADATA_DATABASE_VERSION_CODE = "databaseVersionCode";
    private static final String METADATA_ENTITY_VERSION_CODE_SUFFIX = "_versionCode";
    private static final String METADATA_ENTITY_VERSION_SUFFIX = "_version";
    private static final String METADATA_TABLE_NAME = "odmf_metadata";

    DatabaseHelperTools() {
    }

    public static void createMetadataTable(DataBase db) {
        ODMFSQLiteTableBuilder tableBuilder = new ODMFSQLiteTableBuilder();
        tableBuilder.setSqliteTableName(METADATA_TABLE_NAME);
        tableBuilder.setPrimaryKey(METADATA_COLUMN_NAME, "TEXT", false);
        tableBuilder.addColumn(METADATA_COLUMN_VERSION, "TEXT");
        tableBuilder.createTable(db);
    }

    public static void generateMetadata(DataBase db, ObjectModel model) {
        ContentValues databaseVersion = new ContentValues();
        databaseVersion.put(METADATA_COLUMN_NAME, METADATA_DATABASE_VERSION);
        databaseVersion.put(METADATA_COLUMN_VERSION, model != null ? model.getDatabaseVersion() : "");
        ContentValues databaseVersionCode = new ContentValues();
        databaseVersionCode.put(METADATA_COLUMN_NAME, METADATA_DATABASE_VERSION_CODE);
        databaseVersionCode.put(METADATA_COLUMN_VERSION, Integer.valueOf(model != null ? model.getDatabaseVersionCode() : 1));
        try {
            db.insertOrThrow(METADATA_TABLE_NAME, null, databaseVersion);
            db.insertOrThrow(METADATA_TABLE_NAME, null, databaseVersionCode);
            if (model != null) {
                for (String entityName : model.getEntities().keySet()) {
                    ContentValues entityVersion = new ContentValues();
                    entityVersion.put(METADATA_COLUMN_NAME, model.getEntity(entityName).getTableName() + METADATA_ENTITY_VERSION_SUFFIX);
                    entityVersion.put(METADATA_COLUMN_VERSION, model.getEntity(entityName).getEntityVersion());
                    ContentValues entityVersionCode = new ContentValues();
                    entityVersionCode.put(METADATA_COLUMN_NAME, model.getEntity(entityName).getTableName() + METADATA_ENTITY_VERSION_CODE_SUFFIX);
                    entityVersionCode.put(METADATA_COLUMN_VERSION, Integer.valueOf(model.getEntity(entityName).getEntityVersionCode()));
                    try {
                        db.insertOrThrow(METADATA_TABLE_NAME, null, entityVersion);
                        db.insertOrThrow(METADATA_TABLE_NAME, null, entityVersionCode);
                    } catch (SQLException e) {
                        LOG.logE("Execute generateMetadata Failed : A SQLiteException occurred when generateMetadata");
                        throw new ODMFRuntimeException(e.toString());
                    }
                }
            }
        } catch (SQLException e2) {
            LOG.logE("Execute generateMetadata Failed : A SQLiteException occurred when generateMetadata");
            throw new ODMFRuntimeException(e2.toString());
        }
    }

    public static void createEntityTable(DataBase db, Entity entity) {
        int i;
        Attribute attribute;
        ODMFSQLiteTableBuilder tableBuilder = new ODMFSQLiteTableBuilder();
        tableBuilder.setSqliteTableName(entity.getTableName());
        List<String> entityIDs = new ArrayList();
        if (entity.getEntityId().size() > 1) {
            List<? extends EntityId> entityIds = entity.getEntityId();
            int size = entityIds.size();
            for (i = 0; i < size; i++) {
                entityIDs.add(((EntityId) entityIds.get(i)).getIdName());
            }
            tableBuilder.primaryKey(entityIDs);
        } else {
            EntityId entityId = (EntityId) entity.getEntityId().get(0);
            entityIDs.add(entityId.getIdName());
            String primaryKeyType = DatabaseTableHelper.getColumnType(entityId.getType());
            if (entityId.getGeneratorType().equals(AEntityId.INCREMENT)) {
                tableBuilder.setPrimaryKey(entityId.getIdName(), primaryKeyType, true);
            } else {
                tableBuilder.setPrimaryKey(entityId.getIdName(), primaryKeyType, false);
            }
        }
        List<? extends Attribute> attributeList = entity.getAttributes();
        int attributeSize = attributeList.size();
        for (i = 0; i < attributeSize; i++) {
            attribute = (Attribute) attributeList.get(i);
            int attributeType = attribute.getType();
            if (entityIDs.size() != 1 || !entityIDs.contains(attribute.getColumnName())) {
                tableBuilder.addColumn(attribute.getColumnName(), DatabaseTableHelper.getColumnType(attributeType));
                if (attribute.isNotNull()) {
                    tableBuilder.setNullable(false);
                }
                if (attribute.isUnique()) {
                    tableBuilder.setUnique("ABORT");
                }
                if (attribute.getDefault_value() != null) {
                    if (attributeType == 3) {
                        tableBuilder.setDefaultValue(attribute.getDefault_value().equals("true") ? BusinessTypeEnum.BIZ_TYPE_FENCE : BusinessTypeEnum.BIZ_TYPE_POLICY);
                    } else if (attributeType == 10) {
                        tableBuilder.setDefaultValue(String.valueOf(ThreadLocalDateUtil.parseTime(attribute.getDefault_value()).getTime()));
                    } else if (attributeType == 9 || attributeType == 12 || attributeType == 13) {
                        tableBuilder.setDefaultValue(String.valueOf(ThreadLocalDateUtil.parseDate(attribute.getDefault_value()).getTime()));
                    } else {
                        tableBuilder.setDefaultValue(attribute.getDefault_value());
                    }
                }
            }
        }
        if (entity.getRelationships() != null) {
            List<? extends Relationship> relationships = entity.getRelationships();
            int relationshipSize = relationships.size();
            for (i = 0; i < relationshipSize; i++) {
                Relationship relationship = (Relationship) relationships.get(i);
                if (relationship.getRelationShipType() == 6 && relationship.isMajor()) {
                    tableBuilder.addColumn(relationship.getFieldName(), "INTEGER");
                } else if (relationship.getRelationShipType() == 2) {
                    tableBuilder.addColumn(relationship.getFieldName(), "INTEGER");
                }
            }
        }
        tableBuilder.createTable(db);
        int indexSize = entity.getAttributes().size();
        for (i = 0; i < indexSize; i++) {
            attribute = (Attribute) attributeList.get(i);
            if (attribute.hasIndex()) {
                try {
                    db.execSQL("CREATE INDEX " + entity.getTableName() + "_" + attribute.getColumnName() + "_index ON " + entity.getTableName() + " (" + attribute.getColumnName() + ")");
                } catch (SQLException e) {
                    throw new ODMFRuntimeException(e.toString());
                }
            }
        }
        List<Index> mCompositeIndex = entity.getIndexes();
        if (mCompositeIndex != null) {
            int compositeSize = mCompositeIndex.size();
            i = 0;
            while (i < compositeSize) {
                try {
                    db.execSQL(SqlUtil.createSqlIndex(entity.getTableName(), ((Index) mCompositeIndex.get(i)).getIndexName(), ((Index) mCompositeIndex.get(i)).getCompositeIndexAttributes()));
                    i++;
                } catch (SQLiteException e2) {
                    throw new ODMFRuntimeException(e2.toString());
                }
            }
        }
    }

    public static void handleManyToManyRelationship(DataBase db, Entity entity) {
        int size = entity.getRelationships().size();
        for (int i = 0; i < size; i++) {
            Relationship relationship = (Relationship) entity.getRelationships().get(i);
            if (relationship.getRelationShipType() == 0 && relationship.isMajor()) {
                createManyToManyRelationshipTable(db, relationship);
            }
        }
    }

    public static void createManyToManyRelationshipTable(DataBase db, Relationship relationship) {
        if (isTableExists(db, DatabaseTableHelper.getManyToManyMidTableName(relationship))) {
            LOG.logI("Execute createManyToManyRelationshipTable : The mid-table of this many-to-many relationship already exists, return directly.");
            return;
        }
        ODMFSQLiteTableBuilder tableBuilder = new ODMFSQLiteTableBuilder();
        Entity baseEntity = relationship.isMajor() ? relationship.getBaseEntity() : relationship.getRelatedEntity();
        Entity relatedEntity = relationship.isMajor() ? relationship.getRelatedEntity() : relationship.getBaseEntity();
        tableBuilder.setSqliteTableName(DatabaseTableHelper.getManyToManyMidTableName(relationship));
        tableBuilder.addColumn(DatabaseTableHelper.getRelationshipColumnName(baseEntity), "INTEGER");
        tableBuilder.addColumn(DatabaseTableHelper.getRelationshipColumnName(relatedEntity), "INTEGER");
        tableBuilder.createTable(db);
    }

    private static boolean isTableExists(DataBase db, String tableName) {
        Cursor cursor = null;
        int result = 0;
        try {
            cursor = db.rawQuery("SELECT COUNT(name) FROM sqlite_master WHERE name = ?", new String[]{tableName});
            if (cursor.moveToFirst()) {
                result = cursor.getInt(0);
            }
            if (cursor != null) {
                cursor.close();
            }
            if (result != 0) {
                return true;
            }
            return false;
        } catch (SQLException e) {
            LOG.logE("Execute iSTableExists Failed : A SQLException occurred when addTable");
            throw new ODMFRuntimeException("Execute iSTableExists Failed : " + e.toString());
        } catch (Throwable th) {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public static void addTable(DataBase db, Entity entity) {
        try {
            db.beginTransaction();
            createEntityTable(db, entity);
            handleManyToManyRelationship(db, entity);
            ContentValues values = new ContentValues();
            values.put(METADATA_COLUMN_NAME, entity.getTableName() + METADATA_ENTITY_VERSION_SUFFIX);
            values.put(METADATA_COLUMN_VERSION, entity.getEntityVersion());
            db.insertOrThrow(METADATA_TABLE_NAME, null, values);
            values = new ContentValues();
            values.put(METADATA_COLUMN_NAME, entity.getTableName() + METADATA_ENTITY_VERSION_CODE_SUFFIX);
            values.put(METADATA_COLUMN_VERSION, Integer.valueOf(entity.getEntityVersionCode()));
            db.insertOrThrow(METADATA_TABLE_NAME, null, values);
            db.setTransactionSuccessful();
            db.endTransaction();
        } catch (SQLException e) {
            LOG.logE("Execute addTable Failed : A SQLiteException occurred when addTable");
            throw new ODMFRuntimeException(e.toString());
        } catch (Throwable th) {
            db.endTransaction();
        }
    }

    public static void dropTable(DataBase db, String tableName) {
        try {
            db.beginTransaction();
            db.execSQL(String.format("DROP TABLE IF EXISTS %s;", new Object[]{tableName}));
            db.delete(METADATA_TABLE_NAME, "name = ?", new String[]{tableName + METADATA_ENTITY_VERSION_SUFFIX});
            db.delete(METADATA_TABLE_NAME, "name = ?", new String[]{tableName + METADATA_ENTITY_VERSION_CODE_SUFFIX});
            db.setTransactionSuccessful();
            db.endTransaction();
        } catch (SQLException e) {
            LOG.logE("Execute dropTable Failed : A SQLiteException occurred when dropTable");
            throw new ODMFRuntimeException(e.toString());
        } catch (Throwable th) {
            db.endTransaction();
        }
    }

    public static void alterTableName(DataBase db, String oldEntityName, String newEntityName) {
        String[] oldEntityNames = oldEntityName.split("\\.");
        String[] newEntityNames = newEntityName.split("\\.");
        if (oldEntityNames.length <= 0 || newEntityNames.length <= 0) {
            throw new ODMFIllegalArgumentException("The entityName or the tableName format incorrect.");
        }
        String oldTableName = oldEntityNames[oldEntityNames.length - 1];
        String newTableName = newEntityNames[newEntityNames.length - 1];
        try {
            db.beginTransaction();
            ODMFSQLiteTableBuilder.alterTableName(db, oldTableName, newTableName);
            ContentValues values = new ContentValues();
            values.put(METADATA_COLUMN_NAME, newTableName + METADATA_ENTITY_VERSION_SUFFIX);
            db.update(METADATA_TABLE_NAME, values, "name = ?", new String[]{oldTableName + METADATA_ENTITY_VERSION_SUFFIX});
            values = new ContentValues();
            values.put(METADATA_COLUMN_NAME, newTableName + METADATA_ENTITY_VERSION_CODE_SUFFIX);
            db.update(METADATA_TABLE_NAME, values, "name = ?", new String[]{oldTableName + METADATA_ENTITY_VERSION_CODE_SUFFIX});
            db.setTransactionSuccessful();
            db.endTransaction();
        } catch (SQLException e) {
            LOG.logE("Execute alterTableName Failed : A SQLException occurred when addTable.");
            throw new ODMFRuntimeException("Execute alterTableName Failed : " + e.toString());
        } catch (Throwable th) {
            db.endTransaction();
        }
    }

    static void rebuildMidTable(DataBase db, String oldName, Entity newEntity) {
        String[] entityNames = oldName.split("\\.");
        String tableName = entityNames[entityNames.length - 1];
        int size = newEntity.getRelationships().size();
        for (int i = 0; i < size; i++) {
            if (((Relationship) newEntity.getRelationships().get(i)).getRelationShipType() == 0) {
                String oldMidTableName;
                Entity baseEntity;
                Entity relatedEntity;
                Relationship relationship = (Relationship) newEntity.getRelationships().get(i);
                if (relationship.isMajor()) {
                    oldMidTableName = tableName + "_" + relationship.getRelatedEntity().getTableName() + "_" + relationship.getFieldName();
                    baseEntity = relationship.getBaseEntity();
                    relatedEntity = relationship.getRelatedEntity();
                } else {
                    oldMidTableName = relationship.getRelatedEntity().getTableName() + "_" + tableName + "_" + relationship.getInverseRelationship().getFieldName();
                    baseEntity = relationship.getRelatedEntity();
                    relatedEntity = relationship.getBaseEntity();
                }
                Cursor cursor = null;
                try {
                    db.beginTransaction();
                    createManyToManyRelationshipTable(db, relationship);
                    cursor = db.rawQuery("SELECT * FROM " + oldMidTableName, null);
                    while (cursor.moveToNext()) {
                        ContentValues values = new ContentValues();
                        values.put(DatabaseTableHelper.getRelationshipColumnName(baseEntity), cursor.getString(0));
                        values.put(DatabaseTableHelper.getRelationshipColumnName(relatedEntity), cursor.getString(1));
                        db.insertOrThrow(DatabaseTableHelper.getManyToManyMidTableName(relationship), null, values);
                    }
                    dropTable(db, oldMidTableName);
                    db.setTransactionSuccessful();
                    if (cursor != null) {
                        cursor.close();
                    }
                    db.endTransaction();
                } catch (SQLException e) {
                    LOG.logE("Execute rebuildMidTable Failed : A SQLException occurred when rebuild mid table, it may happened when you want to rename the related table.");
                    throw new ODMFRuntimeException("Execute rebuildMidTable Failed : " + e.toString());
                } catch (Throwable th) {
                    if (cursor != null) {
                        cursor.close();
                    }
                    db.endTransaction();
                }
            }
        }
    }

    static void rebuildMidTable(DataBase db, Entity entity, String newName) {
        String[] newEntityNames = newName.split("\\.");
        String newTableName = newEntityNames[newEntityNames.length - 1];
        int size = entity.getRelationships().size();
        for (int i = 0; i < size; i++) {
            if (((Relationship) entity.getRelationships().get(i)).getRelationShipType() == 0) {
                String newMidTableName;
                String baseColumnName;
                String relatedColumnName;
                Relationship relationship = (Relationship) entity.getRelationships().get(i);
                if (relationship.isMajor()) {
                    newMidTableName = newTableName + "_" + relationship.getRelatedEntity().getTableName() + "_" + relationship.getFieldName();
                    baseColumnName = "ODMF_" + newTableName + "_" + DatabaseQueryService.getRowidColumnName();
                    relatedColumnName = DatabaseTableHelper.getRelationshipColumnName(relationship.getRelatedEntity());
                } else {
                    newMidTableName = relationship.getRelatedEntity().getTableName() + "_" + newTableName + "_" + relationship.getInverseRelationship().getFieldName();
                    baseColumnName = DatabaseTableHelper.getRelationshipColumnName(relationship.getRelatedEntity());
                    relatedColumnName = "ODMF_" + newTableName + "_" + DatabaseQueryService.getRowidColumnName();
                }
                Cursor cursor = null;
                try {
                    db.beginTransaction();
                    ODMFSQLiteTableBuilder tableBuilder = new ODMFSQLiteTableBuilder();
                    tableBuilder.setSqliteTableName(newMidTableName);
                    tableBuilder.addColumn(baseColumnName, "INTEGER");
                    tableBuilder.addColumn(relatedColumnName, "INTEGER");
                    tableBuilder.createTable(db);
                    cursor = db.rawQuery("SELECT * FROM " + DatabaseTableHelper.getManyToManyMidTableName(relationship), null);
                    while (cursor.moveToNext()) {
                        ContentValues values = new ContentValues();
                        values.put(baseColumnName, cursor.getString(0));
                        values.put(relatedColumnName, cursor.getString(1));
                        db.insertOrThrow(newMidTableName, null, values);
                    }
                    dropTable(db, DatabaseTableHelper.getManyToManyMidTableName(relationship));
                    db.setTransactionSuccessful();
                    if (cursor != null) {
                        cursor.close();
                    }
                    db.endTransaction();
                } catch (SQLException e) {
                    LOG.logE("Execute rebuildMidTable Failed : A SQLException occurred when rebuild mid table, it may happened when you want to rename the related table.");
                    throw new ODMFRuntimeException("Execute rebuildMidTable Failed : " + e.toString());
                } catch (Throwable th) {
                    if (cursor != null) {
                        cursor.close();
                    }
                    db.endTransaction();
                }
            }
        }
    }

    public static void alterTableAddRelationship(DataBase db, String tableName, Relationship relationship) {
        if (relationship.getRelationShipType() == 6) {
            if (relationship.isMajor()) {
                alterTableAddColumn(db, tableName, relationship.getFieldName(), "INTEGER");
            } else {
                alterTableAddColumn(db, relationship.getRelatedEntity().getTableName(), relationship.getInverseRelationship().getFieldName(), "INTEGER");
            }
        } else if (relationship.getRelationShipType() == 4) {
            alterTableAddColumn(db, relationship.getRelatedEntity().getTableName(), relationship.getInverseRelationship().getFieldName(), "INTEGER");
        } else if (relationship.getRelationShipType() == 2) {
            alterTableAddColumn(db, relationship.getBaseEntity().getTableName(), relationship.getFieldName(), "INTEGER");
        } else if (relationship.getRelationShipType() == 0) {
            createManyToManyRelationshipTable(db, relationship);
        } else {
            throw new IllegalArgumentException("The relationship type is unsupported.");
        }
    }

    public static void alterTableAddColumn(DataBase db, String tableName, String fieldName, String columnType) {
        ODMFSQLiteTableBuilder builder = new ODMFSQLiteTableBuilder();
        builder.setSqliteTableName(tableName);
        builder.addColumn(fieldName, columnType);
        builder.alterTableAddColumn(db);
    }

    public static void dropTables(DataBase db) {
        Cursor c = null;
        try {
            c = db.rawQuery("select 'drop table if exists ' || name || ';' from sqlite_master where type='table' and name not like 'android%' and name not like 'sqlite%';and name not like 'odmf_metadata';", null);
            while (c.moveToNext()) {
                db.execSQL(c.getString(0));
            }
            if (c != null) {
                c.close();
            }
        } catch (SQLException e) {
            LOG.logE("Execute dropTables Failed : A SQLiteException occurred when dropTables");
            throw new ODMFRuntimeException(e.toString());
        } catch (Throwable th) {
            if (c != null) {
                c.close();
            }
        }
    }

    public static String getDatabaseVersion(DataBase db) {
        String version = null;
        Cursor cursor = null;
        try {
            DataBase dataBase = db;
            cursor = dataBase.query(METADATA_TABLE_NAME, null, "name = ?", new String[]{METADATA_DATABASE_VERSION}, null, null, null, null);
            if (cursor.moveToFirst()) {
                version = cursor.getString(cursor.getColumnIndex(METADATA_COLUMN_VERSION));
            }
            if (cursor != null) {
                cursor.close();
            }
        } catch (SQLException e) {
            LOG.logE("Execute getDatabaseVersion Failed : A SQLiteException occurred when getDatabaseVersion");
            if (cursor != null) {
                cursor.close();
            }
        } catch (Throwable th) {
            if (cursor != null) {
                cursor.close();
            }
        }
        return version;
    }

    public static void setDatabaseVersions(DataBase db, String newVersion, int newVersionCode) {
        if (JudgeUtils.checkVersion(newVersion)) {
            ContentValues versionValues = new ContentValues();
            ContentValues codeValues = new ContentValues();
            versionValues.put(METADATA_COLUMN_VERSION, newVersion);
            codeValues.put(METADATA_COLUMN_VERSION, Integer.valueOf(newVersionCode));
            try {
                db.beginTransaction();
                db.update(METADATA_TABLE_NAME, versionValues, "name = ?", new String[]{METADATA_DATABASE_VERSION});
                db.update(METADATA_TABLE_NAME, codeValues, "name = ?", new String[]{METADATA_DATABASE_VERSION_CODE});
                db.setTransactionSuccessful();
                db.endTransaction();
            } catch (SQLException e) {
                LOG.logE("Execute setDatabaseVersions Failed : A SQLiteException occurred when setDatabaseVersions");
                throw new ODMFRuntimeException(e.toString());
            } catch (Throwable th) {
                db.endTransaction();
            }
        } else {
            LOG.logE("Execute setDatabaseVersion Failed : The newVersion is invalid.");
            throw new ODMFIllegalArgumentException("The newVersion is invalid.");
        }
    }

    public static String getEntityVersion(DataBase db, String tableName) {
        String version = null;
        Cursor cursor = null;
        try {
            DataBase dataBase = db;
            cursor = dataBase.query(METADATA_TABLE_NAME, null, "name = ?", new String[]{tableName + METADATA_ENTITY_VERSION_SUFFIX}, null, null, null, null);
            if (cursor.moveToFirst()) {
                version = cursor.getString(cursor.getColumnIndex(METADATA_COLUMN_VERSION));
            }
            if (cursor != null) {
                cursor.close();
            }
        } catch (SQLException e) {
            LOG.logE("Execute getEntityVersion Failed : A SQLiteException occurred when getEntityVersion");
            if (cursor != null) {
                cursor.close();
            }
        } catch (Throwable th) {
            if (cursor != null) {
                cursor.close();
            }
        }
        return version;
    }

    public static void setEntityVersions(DataBase db, String tableName, String newVersion, int newVersionCode) {
        if (JudgeUtils.checkVersion(newVersion)) {
            ContentValues versionValues = new ContentValues();
            ContentValues codeValues = new ContentValues();
            versionValues.put(METADATA_COLUMN_VERSION, newVersion);
            codeValues.put(METADATA_COLUMN_VERSION, Integer.valueOf(newVersionCode));
            try {
                db.beginTransaction();
                db.update(METADATA_TABLE_NAME, versionValues, "name = ?", new String[]{tableName + METADATA_ENTITY_VERSION_SUFFIX});
                db.update(METADATA_TABLE_NAME, codeValues, "name = ?", new String[]{tableName + METADATA_ENTITY_VERSION_CODE_SUFFIX});
                db.setTransactionSuccessful();
                db.endTransaction();
            } catch (SQLException e) {
                LOG.logE("Execute setEntityVersions Failed : A SQLiteException occurred when setEntityVersions");
                throw new ODMFRuntimeException(e.toString());
            } catch (Throwable th) {
                db.endTransaction();
            }
        } else {
            LOG.logE("Execute setEntityVersion Failed : The newVersion is invalid.");
            throw new ODMFIllegalArgumentException("The newVersion is invalid.");
        }
    }

    public static int getDatabaseVersionCode(DataBase db) {
        int version = 0;
        Cursor cursor = null;
        try {
            DataBase dataBase = db;
            cursor = dataBase.query(METADATA_TABLE_NAME, null, "name = ?", new String[]{METADATA_DATABASE_VERSION_CODE}, null, null, null, null);
            if (cursor.moveToFirst()) {
                version = cursor.getInt(cursor.getColumnIndex(METADATA_COLUMN_VERSION));
            }
            if (cursor != null) {
                cursor.close();
            }
        } catch (SQLException e) {
            LOG.logE("Execute getDatabaseVersion Failed : A SQLiteException occurred when getDatabaseVersion");
            if (cursor != null) {
                cursor.close();
            }
        } catch (Throwable th) {
            if (cursor != null) {
                cursor.close();
            }
        }
        return version;
    }

    public static int getEntityVersionCode(DataBase db, String tableName) {
        int version = 0;
        Cursor cursor = null;
        try {
            DataBase dataBase = db;
            cursor = dataBase.query(METADATA_TABLE_NAME, null, "name = ?", new String[]{tableName + METADATA_ENTITY_VERSION_CODE_SUFFIX}, null, null, null, null);
            if (cursor.moveToFirst()) {
                version = cursor.getInt(cursor.getColumnIndex(METADATA_COLUMN_VERSION));
            }
            if (cursor != null) {
                cursor.close();
            }
        } catch (SQLException e) {
            LOG.logE("Execute getEntityVersionCode Failed : A SQLiteException occurred when getEntityVersionCode");
            if (cursor != null) {
                cursor.close();
            }
        } catch (Throwable th) {
            if (cursor != null) {
                cursor.close();
            }
        }
        return version;
    }

    public static void resetMetadata(DataBase db, ObjectModel model) {
        if (model != null) {
            try {
                db.beginTransaction();
                db.delete(METADATA_TABLE_NAME, null, null);
                generateMetadata(db, model);
                db.setTransactionSuccessful();
                db.endTransaction();
            } catch (SQLException e) {
                LOG.logE("Execute resetMetadata Failed : A SQLiteException occurred when resetMetadata.");
                throw new ODMFRuntimeException(e.toString());
            } catch (Throwable th) {
                db.endTransaction();
            }
        } else {
            LOG.logW("The metadata con not be reset : the model of this database open helper is null.");
        }
    }
}
