/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */

package org.apache.airavata.persistance.registry.jpa.utils;

import java.util.HashMap;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.Query;

public class QueryGenerator {
	private String tableName;
	private Map<String,Object> matches=new HashMap<String, Object>();
	private static final String SELECT_OBJ="p";
	private static final String DELETE_OBJ="p";
	private static final String TABLE_OBJ="p";
	
	public QueryGenerator(String tableName) {
		setTableName(tableName);
	}
	
	public String getTableName() {
		return tableName;
	}
	public void setTableName(String tableName) {
		this.tableName = tableName;
	}
	public void addMatch(String colName, Object matchValue){
		matches.put(colName, matchValue);
	}
	
	public void setParameter(String colName, Object matchValue){
		addMatch(colName, matchValue);
	}
	
	public Query selectQuery(EntityManager entityManager){
		String queryString="SELECT "+SELECT_OBJ+" FROM "+getTableName()+" "+TABLE_OBJ;
		return generateQueryWithParameters(entityManager, queryString);
	}
	
	public Query deleteQuery(EntityManager entityManager){
		String queryString="Delete "+DELETE_OBJ+" FROM "+getTableName()+" "+TABLE_OBJ;
		return generateQueryWithParameters(entityManager, queryString);
	}

	private Query generateQueryWithParameters(EntityManager entityManager,
			String queryString) {
		Map<String,Object> queryParameters=new HashMap<String, Object>();
		if (matches.size()>0){
			String matchString = "";
			int paramCount=0;
			for (String colName : matches.keySet()) {
				String paramName="param"+paramCount;
				queryParameters.put(paramName, matches.get(colName));
				if (!matchString.equals("")){
					matchString+=" AND ";
				}
				matchString+=TABLE_OBJ+"."+colName+" =:"+paramName;
				paramCount++;
			}
			queryString+=" WHERE "+matchString;
		}
		Query query = entityManager.createQuery(queryString);
		for (String paramName : queryParameters.keySet()) {
			query.setParameter(paramName, queryParameters.get(paramName));
		}
		return query;
	}
}