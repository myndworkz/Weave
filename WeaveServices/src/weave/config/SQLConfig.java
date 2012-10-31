/*
 * Weave (Web-based Analysis and Visualization Environment) Copyright (C) 2008-2011 University of Massachusetts Lowell This file is a part of Weave.
 * Weave is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License, Version 3, as published by the
 * Free Software Foundation. Weave is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should have received a copy of the
 * GNU General Public License along with Weave. If not, see <http://www.gnu.org/licenses/>.
 */

package weave.config;

import java.rmi.RemoteException;
import java.security.InvalidParameterException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.w3c.dom.Document;

import weave.utils.SQLUtils;


/**
 * DatabaseConfig This class reads from an SQL database and provides an interface to retrieve strings.
 * 
 * @author Philip Kovac
 * @author Andy Dufilie
 */
public class SQLConfig
		extends ISQLConfig
{
	/* Table name parts */
	private final String SUFFIX_META_PRIVATE = "meta_private";
	private final String SUFFIX_META_PUBLIC = "meta_public";
        private final String SUFFIX_MANIFEST = "manifest";
        private final String SUFFIX_TAGS = "entity_tags";
	private final String WEAVE_TABLE_PREFIX = "weave_";

        /* Complete Table Names */	
	private String table_meta_private = WEAVE_TABLE_PREFIX + SUFFIX_META_PRIVATE;
	private String table_meta_public = WEAVE_TABLE_PREFIX + SUFFIX_META_PUBLIC;
        private String table_manifest = WEAVE_TABLE_PREFIX + SUFFIX_MANIFEST;
        private String table_tags = WEAVE_TABLE_PREFIX + SUFFIX_TAGS;

        /* Column Names */	
	private final String META_ID = "id";
	private final String META_PROPERTY = "property";
	private final String META_VALUE = "value";

        private final String MAN_ID = "unique_id";
        private final String MAN_TYPE = "type_id";
        
        private final String TAG_CHILD = "child_id";
        private final String TAG_PARENT = "parent_id";
        
        /* Constants for type_id */

	private DatabaseConfigInfo dbInfo = null;
	private ISQLConfig connectionConfig = null;
	private Connection _lastConnection = null; // do not use this variable directly -- use getConnection() instead.
        
        protected AttributeValueTable public_attributes;
        protected AttributeValueTable private_attributes;
        protected ManifestTable manifest;
        protected ParentChildTable relationships;
        protected ImmortalConnection connection = null;

	/**
	 * This function gets a connection to the database containing the configuration information. This function will reuse a previously created
	 * Connection if it is still valid.
	 * 
	 * @return A Connection to the SQL database.
	 */
	public Connection getConnection() throws RemoteException, SQLException
	{
		if (SQLUtils.connectionIsValid(_lastConnection))
			return _lastConnection;
		return _lastConnection = connectionConfig.getNamedConnection(dbInfo.connection);
	}

	/**
	 * @param connectionConfig An ISQLConfig instance that contains connection information. This is required because the connection information is not stored in the database.
	 * @param connection The name of a connection in connectionConfig to use for storing and retrieving the data configuration.
	 * @param schema The schema that the data configuration is stored in.
	 * @param geometryConfigTable The table that stores the configuration for geometry collections.
	 * @param dataConfigTable The table that stores the configuration for data tables.
	 * @throws SQLException
	 * @throws InvalidParameterException
	 */
	public SQLConfig(ISQLConfig connectionConfig)
			throws RemoteException, SQLException, InvalidParameterException
	{
		// save original db config info
		dbInfo = connectionConfig.getDatabaseConfigInfo();
		if (dbInfo == null || dbInfo.schema == null || dbInfo.schema.length() == 0)
			throw new InvalidParameterException("DatabaseConfig: Schema not specified.");

		this.connectionConfig = connectionConfig;
		if (getConnection() == null)
			throw new InvalidParameterException("DatabaseConfig: Unable to connect to connection \"" + dbInfo.connection + "\"");

		// attempt to create the schema and tables to store the configuration.
		try
		{
			SQLUtils.createSchema(getConnection(), dbInfo.schema);
		}
		catch (Exception e)
		{
			// do nothing if schema creation fails -- temporary workaround for postgresql issue
			// e.printStackTrace();
		}
                connection = new ImmortalConnection(connectionConfig);
		initSQLTables();
	}
	private void initSQLTables() throws RemoteException, SQLException
	{
	        public_attributes = new AttributeValueTable(connection, dbInfo.schema, table_meta_public);
                private_attributes = new AttributeValueTable(connection, dbInfo.schema, table_meta_private);	
                relationships = new ParentChildTable(connection, dbInfo.schema, table_tags);
                manifest = new ManifestTable(connection, dbInfo.schema, table_manifest);
	/* TODO: Figure out nice way to do this from within the classes. */	
        /*	SQLUtils.addForeignKey(conn, dbInfo.schema, table_meta_private, META_ID, table_manifest, MAN_ID);
		SQLUtils.addForeignKey(conn, dbInfo.schema, table_meta_public, META_ID, table_manifest, MAN_ID);*/
	        
	}
        public boolean isConnectedToDatabase()
        {
                return true;
        }
	synchronized public DatabaseConfigInfo getDatabaseConfigInfo() throws RemoteException
	{
		return connectionConfig.getDatabaseConfigInfo();
	}
	// these functions are just passed to the private connectionConfig
	public Document getDocument() throws RemoteException
	{
		return connectionConfig.getDocument();
	}

	public List<String> getConnectionNames() throws RemoteException
	{
		return connectionConfig.getConnectionNames();
	}

	public void addConnection(ConnectionInfo info) throws RemoteException
	{
		connectionConfig.addConnection(info);
	}

	public ConnectionInfo getConnectionInfo(String connectionName) throws RemoteException
	{
		return connectionConfig.getConnectionInfo(connectionName);
	}

	public void removeConnection(String name) throws RemoteException
	{
		connectionConfig.removeConnection(name);
	}


        public Integer addEntity(Integer type_id, DataEntityMetadata properties) throws RemoteException
        {
            Integer id = manifest.addEntry(type_id);
            if (properties != null)
                updateEntity(id, properties);
            return id;
        }
        private void removeChildren(Integer id) throws RemoteException
        {
            for (Integer child : relationships.getChildren(id))
            {
                removeEntity(child);
            }
        }
        public void removeEntity(Integer id) throws RemoteException
        {
            /* Need to delete all attributeColumns which are children of a table. */
            if (getEntity(id).type == ISQLConfig.DataEntity.MAN_TYPE_DATATABLE)
                removeChildren(id);
            manifest.removeEntry(id);
            relationships.purge(id);
            public_attributes.clearId(id);
            private_attributes.clearId(id);
        }
        public void updateEntity(Integer id, DataEntityMetadata properties) throws RemoteException
        {
            for (Entry<String,String> propval : properties.publicMetadata.entrySet())
            {
                String key = propval.getKey();
                String value = propval.getValue();
                public_attributes.setProperty(id, key, value);
            }
            for (Entry<String,String> propval : properties.privateMetadata.entrySet())
            {
                String key = propval.getKey();
                String value = propval.getValue();
                private_attributes.setProperty(id, key, value);
            }
        }
        public Collection<DataEntity> getEntitiesByType(Integer type_id) throws RemoteException
        {
            return getEntities(manifest.getByType(type_id));
        }
        public Collection<DataEntity> findEntities(DataEntityMetadata properties, Integer type_id) throws RemoteException
        {
            Set<Integer> publicmatches = null;
            Set<Integer> privatematches = null;
            Set<Integer> matches = null;

            if (properties.publicMetadata != null && properties.publicMetadata.size() > 0)
                publicmatches = public_attributes.filter(properties.publicMetadata);
            if (properties.privateMetadata != null && properties.privateMetadata.size() > 0)
                privatematches = private_attributes.filter(properties.privateMetadata);
            /* Ick */
            if ((publicmatches != null) && (privatematches != null))
            {
                publicmatches.retainAll(privatematches);
                matches = publicmatches;
            }
            else if (publicmatches != null)
                matches = publicmatches;
            else if (privatematches != null)
                matches = privatematches;

            if (matches == null || matches.size() < 1)
                return new LinkedList<DataEntity>(); /* return an empty list */
            else
            {
                if (type_id != -1)
                    matches.retainAll(getEntitiesByType(type_id));
                return getEntities(matches);
            }
        }
        public Collection<DataEntity> getEntities(Collection<Integer> ids) throws RemoteException
        {
            List<DataEntity> results = new LinkedList<DataEntity>();
            Map<Integer,Integer> typeresults = manifest.getEntryTypes(ids);
            Map<Integer,Map<String,String>> publicresults = public_attributes.getProperties(ids);
            Map<Integer,Map<String,String>> privateresults = private_attributes.getProperties(ids);
            if (typeresults == null) return results;
            for (Integer id : ids)
            {
                Integer type = typeresults.get(id);
                if (type == null) continue;
                DataEntity tmp = new DataEntity();
                tmp.id = id; 
                tmp.type = type;
                tmp.publicMetadata = publicresults.get(id);
                tmp.privateMetadata = privateresults.get(id);
                results.add(tmp);
            }
            return results;
        }
        public Integer copyEntity(Integer id) throws RemoteException
        {
            /* Do a recursive copy of an entity. */
            Integer new_id;
            DataEntity old_data = getEntity(id);
            new_id = addEntity(old_data.type, old_data);

            Collection<DataEntity> old_children = getChildren(id);
            for (DataEntity child : old_children)
            {
                Integer child_id = child.id;
                if (child.type != ISQLConfig.DataEntity.MAN_TYPE_COLUMN)
                {
                    child_id = copyEntity(child.id);
                }
                addChild(child_id, new_id);
            }
            return new_id;
        }
        public void addChild(Integer child_id, Integer parent_id) throws RemoteException
        {
            relationships.addChild(child_id, parent_id);
        }
        public void removeChild(Integer child_id, Integer parent_id) throws RemoteException
        {
            /* If we're trying to remove a child from a datatable, throw a wobbly. */
            if (manifest.getEntryType(parent_id) == ISQLConfig.DataEntity.MAN_TYPE_DATATABLE)
            {
                throw new RemoteException("Can't remove children from a datatable. Ever.", null);
            }
            relationships.removeChild(child_id, parent_id);
        }
        public Collection<DataEntity> getChildren(Integer id) throws RemoteException
        {
            if (id == -1) id = null;
            Collection<Integer> children_ids = relationships.getChildren(id);
            if (id == null)
            {
                Collection<Integer> completeSet = manifest.getAll();
                completeSet.removeAll(children_ids);
                children_ids = completeSet;
            }
            return getEntities(children_ids);
        }
        public Collection<String> getUniqueValues(String property) throws RemoteException
        {
            if (ISQLConfig.PrivateMetadata.isPrivate(property)) 
            {
                return new HashSet<String>(private_attributes.getProperty(property).values());
            }
            else
            {
                return new HashSet<String>(public_attributes.getProperty(property).values());
            }
        }
/* Abstractions to tidy up the config code. */
        private abstract class AbstractTable
        {
            protected ImmortalConnection conn = null;
            protected String tableName = null;
            protected String schemaName = null;
            public AbstractTable(ImmortalConnection conn, String schemaName, String tableName) throws RemoteException
            {
                this.conn = conn;
                this.tableName = tableName;
                this.schemaName = schemaName;
                if (!tableExists()) initTable();
            }
            protected abstract void initTable() throws RemoteException;
            private boolean tableExists() throws RemoteException
            {
                try
                {
                    Connection conn = this.conn.getConnection();
                    return SQLUtils.tableExists(conn, schemaName, tableName);
                }
                catch (SQLException e)
                {
                    throw new RemoteException("Unable to determine whether table exists.", e);
                }
            }
        }
        private class AttributeValueTable extends AbstractTable
        {
            public AttributeValueTable(ImmortalConnection conn, String schemaName, String tableName) throws RemoteException
            {
                super(conn, schemaName, tableName);
            }
            protected void initTable() throws RemoteException
            {
                try 
                {
                Connection conn = this.conn.getConnection();
                SQLUtils.createTable(conn, schemaName, tableName, 
                    Arrays.asList(META_ID, META_PROPERTY, META_VALUE),
                    Arrays.asList("BIGINT UNSIGNED", "TEXT", "TEXT"));

                } 
                catch (SQLException e)
                {
                    throw new RemoteException("Unable to initialize attribute-value-table.", e);
                }
                try
                {
                    Connection conn = this.conn.getConnection();
                    /* Index of (ID, Property) */
                    SQLUtils.createIndex(conn, schemaName, tableName,
                            tableName+META_ID+META_PROPERTY,
                            new String[]{META_ID, META_PROPERTY},
                            new Integer[]{0, 255});

                    /* Index of (Property, Value) */
                    SQLUtils.createIndex(conn, schemaName, tableName,
                            tableName+META_PROPERTY+META_VALUE,
                            new String[]{META_PROPERTY, META_VALUE},
                            new Integer[]{255,255});
                }
                catch (SQLException e)
                {
                    System.out.println("WARNING: Failed to create index. This may happen if the table already exists.");
                }
            }
            /* TODO: Add optimized methods for adding/removing multiple entries. */
            /* if it is a null or empty string, it will simply unset the property. */
            public void setProperty(Integer id, String property, String value) throws RemoteException
            {
                try 
                {
                    Connection conn = this.conn.getConnection();
                    Map<String, Object> sql_args = new HashMap<String,Object>();
                    sql_args.put(META_PROPERTY, property);
                    sql_args.put(META_ID, id);
                    SQLUtils.deleteRows(conn, schemaName, tableName, sql_args);
                    if (value != null && value.length() > 0)
                    {
                        sql_args.clear();
                        sql_args.put(META_VALUE, value);
                        sql_args.put(META_PROPERTY, property);
                        sql_args.put(META_ID, id);
                        SQLUtils.insertRow(conn, schemaName, tableName, sql_args);
                    }
                } 
                catch (SQLException e)
                {
                    throw new RemoteException("Unable to set property.", e);
                }
            }
            /* Nuke all entries for a given id */
            public void clearId(Integer id) throws RemoteException
            {
                try 
                {
                    Connection conn = this.conn.getConnection();
                    Map<String, Object> sql_args  = new HashMap<String,Object>();
                    sql_args.put(META_ID, id);
                    SQLUtils.deleteRows(conn, schemaName, tableName, sql_args);
                }
                catch (SQLException e)
                {
                    throw new RemoteException("Unable to clear properties for a given id.", e);
                }
            }
            public Map<Integer, String> getProperty(String property) throws RemoteException
            {
                try
                {
                    Connection conn = this.conn.getConnection();
                    Map<String,String> params = new HashMap<String,String>();
                    Map<Integer,String> result = new HashMap<Integer,String>();
                    params.put(META_PROPERTY, property);
                    List<Map<String,Object>> rows = SQLUtils.getRecordsFromQuery(conn, Arrays.asList(META_ID, META_VALUE), schemaName, tableName, params, true);
                    for (Map<String,Object> row : rows)
                    {
                        result.put(Integer.parseInt((String)row.get(META_ID)), (String)row.get(META_VALUE));
                    }
                    return result;
                }
                catch (SQLException e)
                {
                    throw new RemoteException("Unable to get all instances of a property.", e);
                }
            }
            public Map<Integer, Map<String,String>> getProperties(Collection<Integer> ids) throws RemoteException
            {
                try 
                {
                    Connection conn = this.conn.getConnection();
                    return SQLUtils.idInSelect(conn, schemaName, tableName, META_ID, META_PROPERTY, META_VALUE, ids, null);
                }   
                catch (SQLException e)
                {
                    throw new RemoteException("Unable to get properties for a list of ids.", e);
                }
            }
            public Set<Integer> filter(Map<String,String> constraints) throws RemoteException
            {
                try
                {
                    Connection conn = this.conn.getConnection();
                    List<Map<String,String>> crossRowArgs = new LinkedList<Map<String,String>>();

                    for (Entry<String,String> keyValPair : constraints.entrySet())
                    {
                        if (keyValPair.getKey() == null || keyValPair.getValue() == null)
                        	continue;
                        Map<String,String> colValPair = new HashMap<String,String>();
                        colValPair.put(META_PROPERTY, keyValPair.getKey());
                        colValPair.put(META_VALUE, keyValPair.getValue());
                        crossRowArgs.add(colValPair);
                    }
                    return new HashSet<Integer>(SQLUtils.crossRowSelect(conn, schemaName, tableName, META_ID, crossRowArgs));
                }
                catch (SQLException e)
                {
                    throw new RemoteException("Unable to get ids given a set of property/value pairs.", e);
                }
            }
        }
        private class ParentChildTable extends AbstractTable
        {
            public ParentChildTable(ImmortalConnection conn, String schemaName, String tableName) throws RemoteException
            {
                super(conn, schemaName, tableName);
            }
            public void initTable() throws RemoteException
            {
                try 
                {
                    Connection conn = this.conn.getConnection();
                    SQLUtils.createTable(conn, schemaName, tableName,
                        Arrays.asList(TAG_CHILD, TAG_PARENT),
                        Arrays.asList("BIGINT UNSIGNED", "BIGINT UNSIGNED"));
                /* No indices needed. */
                }
                catch (SQLException e)
                {
                    throw new RemoteException("Unable to initialize parent/child table.", e);
                }
            }
            public void addChild(Integer child_id, Integer parent_id) throws RemoteException
            {
                try 
                {
                    Connection conn = this.conn.getConnection();
                    Map<String, Object> sql_args = new HashMap<String,Object>();
                    removeChild(child_id, parent_id);
                    sql_args.put(TAG_CHILD, child_id);
                    sql_args.put(TAG_PARENT, parent_id);
                    SQLUtils.insertRow(conn, schemaName, tableName, sql_args);
                }
                catch (SQLException e)
                {
                    throw new RemoteException("Unable to add child.",e);
                }
            }
            /* getChildren(null) will return all ids that appear in the 'child' column */
            public Collection<Integer> getChildren(Integer parent_id) throws RemoteException
            {
                try
                {
                    Connection conn = this.conn.getConnection();
                    if (parent_id == null)
                    {
                        return new HashSet<Integer>(SQLUtils.getIntColumn(conn, schemaName, tableName, TAG_CHILD));
                    }
                    else 
                    {
                        Map<String,Object> query = new HashMap<String,Object>();
                        Set<Integer> children = new HashSet<Integer>();
                        query.put(TAG_PARENT, parent_id);
                        /* Ew. Need to add properly generic select. Or use JOOQ. */
                        for (Map<String,Object> row : SQLUtils.getRecordsFromQuery(conn, null, schemaName, tableName, query, false))
                        {
                            children.add(((Number)row.get(TAG_CHILD)).intValue());
                        }
                        return children;
                    }
                }
                catch (SQLException e)
                {
                    throw new RemoteException("Unable to retrieve children.");
                }
            }
            /* passing in a null releases the constraint. */
            public void removeChild(Integer child_id, Integer parent_id) throws RemoteException
            {
                try
                {
                    Connection conn = this.conn.getConnection();
                    Map<String,Object> sql_args = new HashMap<String,Object>();
                    if (child_id == null && parent_id == null)
                        throw new RemoteException("removeChild called with two nulls. This is not what you want.", null);
                    if (child_id != null)
                        sql_args.put(TAG_CHILD, child_id);
                    if (parent_id != null) 
                        sql_args.put(TAG_PARENT, parent_id);
                    SQLUtils.deleteRows(conn, schemaName, tableName, sql_args);
                }
                catch (SQLException e)
                {
                    throw new RemoteException("Unable to remove child.", e);
                }
            }
            /* Remove all relationships containing a given parent */
            public void purgeByParent(Integer parent_id) throws RemoteException
            {
                removeChild(null, parent_id);
            }
            /* Remove all relationships containing a given child */
            public void purgeByChild(Integer child_id) throws RemoteException
            {
                removeChild(child_id, null);
            }
            public void purge(Integer id) throws RemoteException
            {
                purgeByChild(id);
                purgeByParent(id);
            }
        }
        private class ManifestTable extends AbstractTable
        {
            public ManifestTable(ImmortalConnection conn, String schemaName, String tableName) throws RemoteException
            {
                super(conn, schemaName, tableName);
            }
            protected void initTable() throws RemoteException
            {
                try
                {
                    Connection conn = this.conn.getConnection();
                    SQLUtils.createTable(conn, schemaName, tableName,
                        Arrays.asList(MAN_ID, MAN_TYPE),
                        Arrays.asList(SQLUtils.getSerialPrimaryKeyTypeString(conn), "TINYINT UNSIGNED"));
                    /* TODO: Add necessary foreign keys. */
                }
                catch (SQLException e)
                {
                    throw new RemoteException("Unable to initialize manifest table.", e);
                }
            }
            public Integer addEntry(Integer type_id) throws RemoteException
            {
                try
                {
                    Connection conn = this.conn.getConnection();
                    Map<String,Object> record = new HashMap<String,Object>();
                    record.put(MAN_TYPE, type_id);
                    return SQLUtils.insertRowReturnID(conn, schemaName, tableName, record);
                }
                catch (SQLException e)
                {
                    throw new RemoteException("Unable to add entry to manifest table.", e);
                }
            }
            public void removeEntry(Integer id) throws RemoteException
            {
                try
                {
                    Connection conn = this.conn.getConnection();
                    Map<String,Object> whereParams = new HashMap<String,Object>();
                    whereParams.put(MAN_ID, id);
                    SQLUtils.deleteRows(conn, schemaName, tableName, whereParams);
                }
                catch (Exception e)
                {
                    throw new RemoteException("Unable to remove entry from manifest table.", e);
                }
            }
            public Integer getEntryType(Integer id) throws RemoteException
            {
                List<Integer> list = new LinkedList<Integer>();
                Map<Integer,Integer> resmap;
                list.add(id);
                resmap = getEntryTypes(list);
                for (Integer idx : resmap.values())
                    return idx;
                throw new RemoteException("No entry exists for this id.", null);
            }
            public Map<Integer,Integer> getEntryTypes(Collection<Integer> ids) throws RemoteException
            {
                /* TODO: Optimize. */
                Map<Integer,Integer> result = new HashMap<Integer,Integer>();
                try
                {
                    Connection conn = this.conn.getConnection();
                    Map<String,Integer> whereParams = new HashMap<String,Integer>();
                    List<Map<String,Object>> sqlres;
                    for (Integer id : ids)
                    {
                        whereParams.clear();
                        whereParams.put(MAN_ID, id);
                        sqlres = SQLUtils.getRecordsFromQuery(conn, Arrays.asList(MAN_ID, MAN_TYPE), schemaName, tableName, whereParams, true);
                        // sqlres has one row
                        result.put(id, new Integer((String)sqlres.get(0).get(MAN_TYPE)));
                    }
                    return result;
                }
                catch (ArrayIndexOutOfBoundsException e)
                {
                    return result;
                }
                catch (Exception e)
                {
                    throw new RemoteException("Unable to get entry types.", e);
                }
            }
            public Collection<Integer> getByType(Integer type_id) throws RemoteException
            {
                try
                {
                    Collection<Integer> ids = new LinkedList<Integer>();
                    Map<String,Integer> whereParams = new HashMap<String,Integer>();
                    List<Map<String,Object>> sqlres;
                    Connection conn = this.conn.getConnection();
                    whereParams.put(MAN_TYPE, type_id);
                    sqlres = SQLUtils.getRecordsFromQuery(conn, Arrays.asList(MAN_ID, MAN_TYPE), schemaName, tableName, whereParams, true);
                    for (Map<String,Object> row : sqlres)
                    {
                        ids.add(Integer.parseInt((String)row.get(MAN_ID)));
                    }
                    return ids;
                }
                catch (Exception e)
                {
                    throw new RemoteException("Unable to get by type.", e);
                }
            }
            public Collection<Integer> getAll() throws RemoteException
            {
                try
                {
                    Connection conn = this.conn.getConnection();
                    return new HashSet<Integer>(SQLUtils.getIntColumn(conn, schemaName, tableName, MAN_ID));
                }
                catch (Exception e)
                {
                    throw new RemoteException("Unable to get complete manifest.", e);
                } 
            }
        }
}
