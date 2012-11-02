/*
 * Weave (Web-based Analysis and Visualization Environment) Copyright (C) 2008-2011 University of Massachusetts Lowell This file is a part of Weave.
 * Weave is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License, Version 3, as published by the
 * Free Software Foundation. Weave is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. You should have received a copy of the
 * GNU General Public License along with Weave. If not, see <http://www.gnu.org/licenses/>.
 */

package weave.config.tables;

import java.rmi.RemoteException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import weave.config.ISQLConfig.ImmortalConnection;
import weave.utils.SQLUtils;


/**
 * @author Philip Kovac
 */
public class ManifestTable extends AbstractTable
{
	private final String MAN_ID = "unique_id";
	private final String MAN_TYPE = "type_id";
	
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
            Map<String,Object> whereParams = new HashMap<String,Object>();
            List<Map<String,Object>> sqlres;
            for (Integer id : ids)
            {
                whereParams.put(MAN_ID, id);
                sqlres = SQLUtils.getRecordsFromQuery(conn, Arrays.asList(MAN_ID, MAN_TYPE), schemaName, tableName, whereParams, Object.class);
                // sqlres has one row
                Number type = (Number) sqlres.get(0).get(MAN_TYPE);
                result.put(id, type.intValue());
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
            Map<String,Object> whereParams = new HashMap<String,Object>();
            List<Map<String,Object>> sqlres;
            Connection conn = this.conn.getConnection();
            whereParams.put(MAN_TYPE, type_id);
            sqlres = SQLUtils.getRecordsFromQuery(conn, Arrays.asList(MAN_ID, MAN_TYPE), schemaName, tableName, whereParams, Object.class);
            for (Map<String,Object> row : sqlres)
            {
            	Number id = (Number) row.get(MAN_ID);
                ids.add(id.intValue());
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