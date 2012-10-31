/*
    Weave (Web-based Analysis and Visualization Environment)
    Copyright (C) 2008-2011 University of Massachusetts Lowell

    This file is a part of Weave.

    Weave is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License, Version 3,
    as published by the Free Software Foundation.

    Weave is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with Weave.  If not, see <http://www.gnu.org/licenses/>.
*/
package weave.reports;

import java.rmi.RemoteException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import weave.config.ISQLConfig;
import weave.config.ISQLConfig.DataEntity;
import weave.config.ISQLConfig.DataEntityMetadata;
import weave.config.ISQLConfig.PublicMetadata;
import weave.utils.SQLResult;
import weave.utils.SQLUtils;

public class AttributeColumnData
{
	public AttributeColumnData(ISQLConfig config)
	{
		this.config = config;
	}
	
	private ISQLConfig config;
	public List<String> data = new ArrayList<String>();
	public List<String> keys = new ArrayList<String>();
	
	//get data for the entire column 
	public void getData(String dataTableName, String attributeColumnName, String year) 
		throws RemoteException
	{
		getData(dataTableName, attributeColumnName, year, null);
	}
	
	
	//only get data for the subset of keys
	public int getData(String dataTableName, String attributeColumnName, String year, List<String> reportKeys)
		throws RemoteException		
	{
		if (dataTableName == null || attributeColumnName == null || (dataTableName.length() == 0) || (attributeColumnName.length() == 0))
			throw new RemoteException("Invalid request for DataTable \""+dataTableName+"\", AttributeColumn \""+attributeColumnName+"\"");
		
		//clear out previous data
		data.clear();
		keys.clear();
		
		//get query
		DataEntityMetadata params = new DataEntityMetadata();
		params.publicMetadata.put(PublicMetadata.DATATABLE, dataTableName);
		params.publicMetadata.put(PublicMetadata.NAME, attributeColumnName);
		if ((year != null) && (year.length() > 0))
			params.publicMetadata.put(PublicMetadata.YEAR, year);
		Collection<DataEntity> infoList = config.findEntities(params, DataEntity.MAN_TYPE_DATATABLE);
                DataEntity info = null;
                for (DataEntity i : infoList)
                {
                    info = i;
                    break;
                }
		String connection = info.getConnectionName();
		String dataWithKeysQuery = info.getSqlQuery();
		
		//run query to get resulting rowset
		SQLResult result;
		try
		{
                        Connection conn = config.getNamedConnection(connection, true);
                        result = SQLUtils.getRowSetFromQuery(conn, dataWithKeysQuery);
		}
		catch (SQLException e)
		{
			e.printStackTrace();
			throw new RemoteException(e.getMessage());
		}
		
		//loop through rowset putting data into this column
		Object keyValueObj = null;
		Object dataValueObj = null;
		for (int i = 0; i < result.rows.length; i++)
		{
			keyValueObj = result.rows[i][0];
			if ((reportKeys == null) || (reportKeys.contains(keyValueObj)))
			{
				dataValueObj = result.rows[i][1];
				if ((keyValueObj != null) && (dataValueObj != null))
				{
					keys.add(keyValueObj.toString());
					data.add(dataValueObj.toString());
				}
			}
		}
		return keys.size();
	}

	public String getDataForKey(String key)
	{
		int iKey = keys.lastIndexOf(key);
		if (iKey >= 0)
			return data.get(iKey);
		else
			return null;
	}
	
}
