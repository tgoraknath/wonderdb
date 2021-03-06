package org.wonderdb.query.plan;

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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import org.wonderdb.cluster.Shard;
import org.wonderdb.core.collection.WonderDBList;
import org.wonderdb.expression.AndExpression;
import org.wonderdb.expression.BasicExpression;
import org.wonderdb.expression.Expression;
import org.wonderdb.expression.Operand;
import org.wonderdb.expression.VariableOperand;
import org.wonderdb.query.parse.CollectionAlias;
import org.wonderdb.query.parse.StaticOperand;
import org.wonderdb.schema.CollectionMetadata;
import org.wonderdb.schema.SchemaMetadata;
import org.wonderdb.types.IndexNameMeta;

public class QueryPlanBuilder {
	private static QueryPlanBuilder builder = new QueryPlanBuilder();
	private QueryPlanBuilder() {
	}
	
	public static QueryPlanBuilder getInstance() {
		return builder;
	}
	
	public List<QueryPlan> build(List<CollectionAlias> collectionNames, Shard shard, AndExpression exp, Set<Object> pinnedBlocks) {
		List<QueryPlan> qPlan = new ArrayList<QueryPlan>();
		
		if (exp == null || exp.getExpList() == null || exp.getExpList().size() == 0) {
			for (int i = 0; i < collectionNames.size(); i++) {
				CollectionMetadata colMeta = SchemaMetadata.getInstance().getCollectionMetadata(collectionNames.get(i).getCollectionName());
				WonderDBList dbList = colMeta.getRecordList(new Shard(""));
				qPlan.add(new FullTableScan(dbList, collectionNames.get(i), pinnedBlocks));
			}
			return qPlan;
		}

		List<BasicExpression> list1 = exp.getExpList();
		List<BasicExpression> list = filterNonIndexable(list1);
		
		if (list == null || list.size() == 0 ) {
			for (int i = 0; i < collectionNames.size(); i++) {
				CollectionMetadata colMeta = SchemaMetadata.getInstance().getCollectionMetadata(collectionNames.get(i).getCollectionName());
				WonderDBList dbList = colMeta.getRecordList(new Shard(""));
				qPlan.add(new FullTableScan(dbList, collectionNames.get(i), pinnedBlocks));
			}
		}
	
		Set<CollectionAlias> reducedCollections = new HashSet<CollectionAlias>();
		Map<CollectionAlias, List<BasicExpression>> expListByCollection = separateExpressionsByCollection(list);
		
		while (reducedCollections.size() < collectionNames.size()) {
			List<BasicExpression> staticExpressions = getStaticExpressions(list, reducedCollections);
			Map<CollectionAlias, Set<Integer>> staticCollectionsMap = separateColumnsByCollections(staticExpressions);
			Map<CollectionAlias, IndexNameMeta> bestStaticCollectionsIndex = getBestMatchIndex(staticCollectionsMap, reducedCollections);
			if (bestStaticCollectionsIndex.size() == 0) {
				for (int i = 0; i < collectionNames.size(); i++) {
					CollectionAlias ca = collectionNames.get(i);
					
					if (!reducedCollections.contains(ca)) {
						CollectionMetadata colMeta = SchemaMetadata.getInstance().getCollectionMetadata(ca.getCollectionName());
						WonderDBList dbList = colMeta.getRecordList(new Shard(""));
						reducedCollections.add(ca);
						qPlan.add(new FullTableScan(dbList, ca, pinnedBlocks));
						break;
					}
				}
			} else {
				Iterator<CollectionAlias> iter = bestStaticCollectionsIndex.keySet().iterator();
				while (iter.hasNext()) {
					CollectionAlias ca = iter.next();
					if (!reducedCollections.contains(ca)) {
						qPlan.add(new IndexRangeScan(ca, bestStaticCollectionsIndex.get(ca), shard, expListByCollection.get(ca), null, pinnedBlocks));
						reducedCollections.add(ca);
					}
				}
			}
		}

		return qPlan;
	}	
	
	
	
	private Map<CollectionAlias, List<BasicExpression>> separateExpressionsByCollection(List<BasicExpression> list) {
		Map<CollectionAlias, List<BasicExpression>> retVal = new HashMap<CollectionAlias, List<BasicExpression>>();
		for (int i = 0; i < list.size(); i++) {
			BasicExpression exp = list.get(i);
			Operand left = exp.getLeftOperand();
			Operand right = exp.getRightOperand();
			if (left instanceof VariableOperand) {
				VariableOperand vo = (VariableOperand) left;
				List<BasicExpression> l = retVal.get(vo.getCollectionAlias());
				if (l == null) {
					l = new ArrayList<BasicExpression>();
					retVal.put(vo.getCollectionAlias(), l);
				}
				l.add(exp);
			}
			if (right instanceof VariableOperand) {
				VariableOperand vo = (VariableOperand) right;
				List<BasicExpression> l = retVal.get(vo.getCollectionAlias());
				if (l == null) {
					l = new ArrayList<BasicExpression>();
					retVal.put(vo.getCollectionAlias(), l);
				}
				l.add(exp);
			}
		}
		return retVal;
	}
	
	private List<BasicExpression> getStaticExpressions(List<BasicExpression> list, Set<CollectionAlias> reducedCollections) {
		List<BasicExpression> retList = new ArrayList<BasicExpression>();
		for (int i = 0; i < list.size(); i++) {
			BasicExpression exp = list.get(i);
			if (exp.getLeftOperand() instanceof StaticOperand || exp.getRightOperand() instanceof StaticOperand) {
				retList.add(exp);
				continue;
			} 
			
			if (exp.getLeftOperand() instanceof VariableOperand) {
				VariableOperand vo = (VariableOperand) exp.getLeftOperand();
				if (reducedCollections.contains(vo.getCollectionAlias())) {
					retList.add(exp);
					continue;
				}
			} 
			
			if (exp.getRightOperand() instanceof VariableOperand) {
				VariableOperand vo = (VariableOperand) exp.getRightOperand();
				if (reducedCollections.contains(vo.getCollectionAlias())) {
					retList.add(exp);
					continue;
				}
			}
		}
		return retList;
	}
	
	@SuppressWarnings("unused")
	private List<QueryPlan> orderPlans(List<QueryPlan> plans, List<BasicExpression> expList) {
		List<QueryPlan> finalOrder = new ArrayList<QueryPlan>();
		Set<CollectionAlias> availableCollections = new HashSet<CollectionAlias>();
		while (true) {
			if (plans.size() == 0) {
				break;
			}
			QueryPlan p = plans.remove(0);
			if (p instanceof FullTableScan) {
				finalOrder.add(p);
				availableCollections.add(p.getCollectionAlias());
				continue;
			}
			IndexRangeScan irc = null;
			if (p instanceof IndexRangeScan) {
				irc = (IndexRangeScan) p;
			}
			if (isReducible(irc, expList, availableCollections)) {
				finalOrder.add(p);
				availableCollections.add(p.getCollectionAlias());
			} else {
				plans.add(p);
			}
		}
		return finalOrder;
	}
	
	private boolean isReducible(IndexRangeScan p, List<BasicExpression> expList, Set<CollectionAlias> availableCollections) {
		IndexNameMeta idx = p.getIndex();
		List<Integer> indexCols = idx.getColumnIdList();
		CollectionAlias ca = p.getCollectionAlias();
		
		for (int i = 0; i < expList.size(); i++) {
			BasicExpression exp = expList.get(i);
			Operand l = exp.getLeftOperand();
			Operand r = exp.getRightOperand();
			if (l instanceof VariableOperand) {
				VariableOperand left = (VariableOperand) l;
				if (left.getCollectionAlias().equals(ca)) {
					if (left.getColumnId().equals(indexCols.get(0))) {
						if (r instanceof StaticOperand) {
							return true;
						}
						if (r instanceof VariableOperand) {
							VariableOperand right = (VariableOperand) r;
							if (availableCollections.contains(right.getCollectionAlias())) {
								return true;
							}
						}
					}
				}
			}
			if (l instanceof StaticOperand) {
				if (r instanceof StaticOperand) {
					continue;
				}
				if (r instanceof VariableOperand) {
					VariableOperand right = (VariableOperand) r;
					if (right.getCollectionAlias().equals(ca)) {
						return true;
					}
				}
			}

			if (r instanceof VariableOperand) {
				VariableOperand right = (VariableOperand) r;
				if (right.getCollectionAlias().equals(ca)) {
					if (right.getColumnId().equals(indexCols.get(0))) {
						if (l instanceof StaticOperand) {
							return true;
						}
						if (l instanceof VariableOperand) {
							VariableOperand left = (VariableOperand) l;
							if (availableCollections.contains(left.getCollectionAlias())) {
								return true;
							}
						}
					}
				}
			}
			if (r instanceof StaticOperand) {
				if (l instanceof StaticOperand) {
					continue;
				}
				if (l instanceof VariableOperand) {
					VariableOperand left = (VariableOperand) l;
					if (left.getCollectionAlias().equals(ca)) {
						return true;
					}
				}
			}
		}
		return false;
	}
	
	@SuppressWarnings("unused")
	private void orderBasedOnReahability(List<QueryPlan> qPlan, Map<CollectionAlias, Set<String>> map, List<BasicExpression> expList) {
		// first pass
		// put all full table scans in reachable Columns list.
		Map<CollectionAlias, Set<String>> reachableColumns = new HashMap<CollectionAlias, Set<String>>();
		List<QueryPlan> finalOrder = new ArrayList<QueryPlan>();
		Stack<QueryPlan> reachableOrder = new Stack<QueryPlan>();
		
		for (int i = 0; i < qPlan.size(); i++) {
			QueryPlan p = qPlan.get(i); 
			if (p instanceof FullTableScan) {
				finalOrder.add(p);
				Set<String> set = map.get(p.getCollectionAlias());
				if (set != null && set.size() > 0) {
					reachableColumns.put(p.getCollectionAlias(), set);
				}
			}
		}
		
		for (int i = 0; i < qPlan.size(); i++) {
			QueryPlan p = qPlan.get(i);
			if (p instanceof FullTableScan) {
				continue;
			}
			IndexRangeScan irs = (IndexRangeScan) p;
			isReachable(irs, reachableColumns, expList);
		}
	}
	
	
	@SuppressWarnings("unused")
	private boolean isReachable(IndexRangeScan irs, Map<CollectionAlias, Set<String>> reachableColumns, List<BasicExpression> expList) {
		Set<String> idxCols = new HashSet<String>();
		
		for (int i = 0; i < irs.getIndex().getColumnIdList().size(); i++) {
			String columnName = SchemaMetadata.getInstance().getColumnName(irs.getIndex().getCollectionName(), irs.getIndex().getColumnIdList().get(i));
			idxCols.add(columnName);
		}
		
		for (int i = 0; i < expList.size(); i++) {
			Operand left = expList.get(i).getLeftOperand();
			Operand right = expList.get(i).getRightOperand();
			VariableOperand l = null;
			if (left instanceof VariableOperand) {
				l = (VariableOperand) left;
				if (l.getCollectionAlias().equals(irs.collectionAlias) && idxCols.contains(l.getColumnId())) {
//					if (right instanceof StaticOperand)
				}
			}
		}
		return false;
	}
	
	
	private boolean isOneStaticIndex(Operand o, Map<CollectionAlias, IndexNameMeta> bestMatchIndex) {
		boolean oneStaticIndex = false;
		if (o instanceof VariableOperand) {
			VariableOperand vo = (VariableOperand) o;
			CollectionAlias ca = new CollectionAlias(vo.getCollectionAlias());
			IndexNameMeta idx = bestMatchIndex.get(ca);
			if (idx != null && idx.getColumnIdList().get(0) == vo.getColumnId()) {
				// we found it!
				// we will use index all the way
				oneStaticIndex = true;
			}
		}
		return oneStaticIndex;
	}
	
	@SuppressWarnings("unused")
	private boolean isOneStaticIndex(List<BasicExpression> list, Map<CollectionAlias, IndexNameMeta> bestMatchIndex) {
		boolean oneStaticIndex = false;
		for (int i = 0; i < list.size(); i++) {
			BasicExpression bexp = list.get(i);
			Operand o = bexp.getLeftOperand();

			if (o instanceof StaticOperand) {
				Operand r = bexp.getRightOperand();
				oneStaticIndex = isOneStaticIndex(r, bestMatchIndex);
				if (oneStaticIndex) {
					break;
				}
			}
			o = bexp.getRightOperand();
			if (o instanceof StaticOperand) {
				Operand l = bexp.getLeftOperand();
				oneStaticIndex = isOneStaticIndex(l, bestMatchIndex);
				if (oneStaticIndex) {
					break;
				}
			}
		}
		return oneStaticIndex;
	}
	
	
	private List<BasicExpression> filterNonIndexable(List<BasicExpression> list) {
		List<BasicExpression> retVal = new ArrayList<BasicExpression>();
		if (list == null || list.size() == 0) {
			return retVal;
		}
		
		for (int i = 0; i < list.size(); i++) {
			BasicExpression exp = list.get(i);
			int op = exp.getOperator();
			if (op != Expression.OR &&
					op != Expression.IN &&
					op != Expression.NE) {
				retVal.add(exp);
			}
		}
		return retVal;
	}
	
	
	private Map<CollectionAlias, IndexNameMeta> getBestMatchIndex(Map<CollectionAlias, Set<Integer>> map, Set<CollectionAlias> reducedCollections) {
		Map<CollectionAlias, IndexNameMeta> retMap = new HashMap<CollectionAlias, IndexNameMeta>();
		Iterator<CollectionAlias> iter = map.keySet().iterator();
		while (iter.hasNext()) {
			CollectionAlias collectionName = iter.next();
			if (!reducedCollections.contains(collectionName)) {
				Set<Integer> colSet = map.get(collectionName);
				IndexNameMeta idx = getBestMatchIndex(collectionName, colSet);
				if (idx != null) {
					retMap.put(collectionName, getBestMatchIndex(collectionName, colSet));
				}
			}
		}
		return retMap;
	}

	IndexNameMeta getBestMatchIndex(CollectionAlias collectionAlias, Set<Integer> colSet) {
		List<IndexNameMeta> idxList = SchemaMetadata.getInstance().getIndexes(collectionAlias.getCollectionName());
		if (idxList == null || idxList.size() == 0) {
			return null;
		}
		
		int maxMatchCount = 0;
		IndexNameMeta currentBestIndex = null;
		for (int i = 0; i < idxList.size(); i++) {
			int colMatchCount = 0;
			IndexNameMeta meta = idxList.get(i);
			List<Integer> colList = meta.getColumnIdList();
			for (int x = 0; x < colList.size(); x++) {
				Integer col = colList.get(x);
				if (colSet.contains(col)) {
					colMatchCount++;
				}
			}
			if (colMatchCount > maxMatchCount) {
				maxMatchCount = colMatchCount;
				currentBestIndex = meta;
			}
		}
		return currentBestIndex;
	}
	
	private Map<CollectionAlias, Set<Integer>> separateColumnsByCollections(List<BasicExpression> list) {
		Map<CollectionAlias, Set<Integer>> map = new HashMap<CollectionAlias, Set<Integer>>();
		
		for (int i = 0; i < list.size(); i++) {
			BasicExpression exp = list.get(i);
			Operand o = exp.getLeftOperand();
			if (o instanceof VariableOperand) {
				VariableOperand vo = (VariableOperand) o;
				operandToColumn(vo, map);
			}
			o = exp.getRightOperand();
			if (o instanceof VariableOperand) {
				VariableOperand vo = (VariableOperand) o;
				operandToColumn(vo, map);
			}			
		}		
		return map;
	}
	
	private void operandToColumn(VariableOperand vo, Map<CollectionAlias, Set<Integer>> map) {
		Set<Integer> s = map.get(vo.getCollectionAlias());
		if (s == null) {
			s = new HashSet<Integer>();
			map.put(new CollectionAlias(vo.getCollectionAlias()), s);
		}
		s.add(vo.getColumnId());
	}	
	
	@SuppressWarnings("unused")
	private void fixExpList(List<BasicExpression> expList) {
		for (int i = 0; i < expList.size(); i++) {
			BasicExpression exp = expList.get(i);
			if (exp.getLeftOperand() instanceof StaticOperand && exp.getRightOperand() instanceof VariableOperand && exp.getOperator() == Expression.EQ) {
				replaceOperands(expList, exp.getRightOperand(), exp.getLeftOperand());
			} 
			if (exp.getLeftOperand() instanceof VariableOperand && exp.getRightOperand() instanceof StaticOperand && exp.getOperator() == Expression.EQ) {
				replaceOperands(expList, exp.getLeftOperand(), exp.getRightOperand());
			}
		}
	}
	
	private void replaceOperands(List<BasicExpression> expList, Operand op1, Operand op2) {
		for (int i = 0; i < expList.size(); i++) {
			Operand left = expList.get(i).getLeftOperand();
			Operand right = expList.get(i).getRightOperand();
			if (left instanceof VariableOperand && right instanceof VariableOperand) {
				if (left.equals(op1)) {
					expList.get(i).setLeftOperand(op2);
				}
				if (right.equals(op1)) {
					expList.get(i).setRightOperand(op2);
				}
			}
		}
	}	
}
