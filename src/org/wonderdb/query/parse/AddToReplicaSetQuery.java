/*******************************************************************************
 *    Copyright 2013 Vilas Athavale
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *******************************************************************************/
package org.wonderdb.query.parse;

import org.wonderdb.cluster.ClusterManagerFactory;
import org.wonderdb.expression.AndExpression;
import org.wonderdb.parser.UQLParser.AddToReplicaSetStmt;


public class AddToReplicaSetQuery extends BaseDBQuery {
	AddToReplicaSetStmt stmt;
	
	public AddToReplicaSetQuery(String query, AddToReplicaSetStmt stmt){
		super(query, -1, null);
		this.stmt = stmt;
	}
	
	public void execute() {
		stmt.nodeId = stmt.nodeId.replaceAll("'", "").trim();
		ClusterManagerFactory.getInstance().getClusterManager().addToReplicaSet(stmt.replicaSetName, stmt.nodeId);
	}
	
	@Override
	public AndExpression getExpression() {
		return null;
	}
}
