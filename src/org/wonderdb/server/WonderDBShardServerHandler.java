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
package org.wonderdb.server;

import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.wonderdb.collection.exceptions.InvalidCollectionNameException;
import org.wonderdb.collection.exceptions.InvalidIndexException;
import org.wonderdb.collection.exceptions.UniqueKeyViolationException;
import org.wonderdb.file.FileAlreadyExisits;
import org.wonderdb.file.StorageFileCreationException;
import org.wonderdb.query.executor.ScatterGatherQueryExecutor;
import org.wonderdb.query.parse.AddToReplicaSetQuery;
import org.wonderdb.query.parse.CreateIndexQuery;
import org.wonderdb.query.parse.CreateReplicaSetQuery;
import org.wonderdb.query.parse.CreateShardQuery;
import org.wonderdb.query.parse.CreateTableQuery;
import org.wonderdb.query.parse.DBCreateStorageQuery;
import org.wonderdb.query.parse.DBDeleteQuery;
import org.wonderdb.query.parse.DBInsertQuery;
import org.wonderdb.query.parse.DBQuery;
import org.wonderdb.query.parse.DBSelectQuery;
import org.wonderdb.query.parse.DBSelectQuery.ResultSetColumn;
import org.wonderdb.query.parse.DBSelectQuery.ResultSetValue;
import org.wonderdb.query.parse.DBShowPlanQuery;
import org.wonderdb.query.parse.DBUpdateQuery;
import org.wonderdb.query.parse.QueryParser;
import org.wonderdb.query.parse.ShowIndexQuery;
import org.wonderdb.query.parse.ShowSchemaQuery;
import org.wonderdb.query.parse.ShowStoragesQuery;
import org.wonderdb.query.parse.ShowTableQuery;
import org.wonderdb.query.plan.FullTableScan;
import org.wonderdb.query.plan.IndexRangeScan;
import org.wonderdb.query.plan.QueryPlan;
import org.wonderdb.seralizers.BlockPtrSerializer;
import org.wonderdb.seralizers.CollectionColumnSerializer;
import org.wonderdb.seralizers.SerializerManager;
import org.wonderdb.seralizers.StringSerializer;
import org.wonderdb.types.DBType;
import org.wonderdb.types.SerializableType;
import org.wonderdb.types.impl.StringType;



public class WonderDBShardServerHandler extends SimpleChannelHandler {
	public static final int SERVER_HANDLER = 0;
	public static final int SHARD_HANDLER = 1;
	
	int handlerType = SHARD_HANDLER;
	
	public WonderDBShardServerHandler(int type) {
		this.handlerType = type;
	}
	
	public int getHandlerType() {
		return handlerType;
	}
	
    @SuppressWarnings("unused")
	@Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) {
	    	
    	ChannelBuffer buf = (ChannelBuffer) e.getMessage();
    	ChannelBuffer buffer = ChannelBuffers.copiedBuffer(buf);
    	buffer.clear();
    	buffer.writerIndex(buffer.capacity());
    	int queryLen = buffer.readInt();
    	byte[] bytes = new byte[queryLen];
    	buffer.readBytes(bytes);
    	String s = new String(bytes);
    	List<DBType> bindParamList = new ArrayList<DBType>();
    	int bindParamCount = buffer.readInt();
    	for (int i = 0; i < bindParamCount; i++) {
    		byte b = buffer.readByte();
    		if (b == 0) {
    			bindParamList.add(new StringType(null));
    			continue;
    		}
    		int size = buffer.readInt();
    		bytes = new byte[size];
    		buffer.readBytes(bytes);
    		bindParamList.add(new StringType(new String(bytes)));
    	}
    	
    	int version = buffer.readInt();
    	ChannelBuffer outBuf = ChannelBuffers.dynamicBuffer();
    	Channel channel = e.getChannel();
    	
    	try {
	    	if ("shutdown".equals(s)) {
	    		synchronized (WonderDBServer.lock) {
	    			WonderDBServer.shutdown = true;
	    			WonderDBServer.lock.notifyAll();

	    			outBuf.clear();
	    	    	outBuf.writeInt(8);
	    	    	outBuf.writeByte((byte) 1);
	    	    	outBuf.writeInt(0);
	    	    	outBuf.writeInt(0);
				}
	    	} else {
		    	String query = s;
		    	if (query.startsWith("explain plan")) {
//		    		handleValidate(outBuf, channel);
		    		DBShowPlanQuery q = new DBShowPlanQuery(query, bindParamList);
		    		handleExplainPlan(q, outBuf, channel);
		    	} else if (query.equals("show storages")) {
		    		ShowStoragesQuery q = new ShowStoragesQuery();
		    		handleShowStoragesQuery(q, channel);
		    	} else {
			    	QueryParser parser = QueryParser.getInstance();
			    	DBQuery q = parser.parse(query, bindParamList, handlerType, buffer);
			    	if (q instanceof DBSelectQuery) {
			    		handleSelectQuery((DBSelectQuery) q, outBuf, channel);
			    	} else if (q instanceof DBInsertQuery) {
			    		handleInsertQuery((DBInsertQuery) q, outBuf);
			    	} else if (q instanceof DBUpdateQuery) {
			    		handleUpdateQuery((DBUpdateQuery) q, outBuf);
			    	} else if (q instanceof DBDeleteQuery) {
			    		handleDeleteQuery((DBDeleteQuery) q, outBuf);
			    	} else if (q instanceof CreateIndexQuery) {
			    		handleCreateIndexQuery((CreateIndexQuery) q, outBuf);
			    	} else if (q instanceof CreateTableQuery) {
			    		handleCreateTableQuery((CreateTableQuery) q, outBuf);
			    	} else if (q instanceof ShowTableQuery) {
			    		handleShowTableQuery((ShowTableQuery) q, channel);
			    	} else if (q instanceof ShowIndexQuery) {
			    		handleShowIndexQuery((ShowIndexQuery) q, channel);
			    	}else if (q instanceof ShowSchemaQuery) {
			    		handleShowSchemaQuery((ShowSchemaQuery) q, channel);
			    	} else if (q instanceof DBCreateStorageQuery) {
			    		handleCreateStorageQuery((DBCreateStorageQuery) q, outBuf);
			    	} else if (q instanceof CreateShardQuery) {
			    		handleCreateShardQuery((CreateShardQuery) q, outBuf);
			    	} else if (q instanceof CreateReplicaSetQuery) {
			    		handleCreateReplicaSetQuery((CreateReplicaSetQuery) q, outBuf);
			    	} else if (q instanceof AddToReplicaSetQuery) {
			    		handleAddToReplicaSetQuery((AddToReplicaSetQuery) q, outBuf);
			    	}
		    	}
	    	} 
    	} catch (Throwable t) {
    		outBuf.writeInt(0);
    		outBuf.writeByte(1);
    		t.printStackTrace();
//    		t.printStackTrace();
//    		String s1 = "Exception :" + t.getMessage();
//    		outBuf.writeInt(s1.getBytes().length);
//    		outBuf.writeBytes(s1.getBytes());
    	}
    	channel.write(outBuf);
    } 

//    private void handleValidate(ChannelBuffer buffer, Channel channel) {
//    	Map<String, CollectionMetadata> map = SchemaMetadata.getInstance().getCollections();
//    	Iterator<String> iter = map.keySet().iterator();
//    	IndexKeyType previkt = null;
//    	Set<BlockPtr> pinnedBlocks = new HashSet<BlockPtr>();
//    	while (iter.hasNext()) {
//    		String colName = iter.next();
//			int schemaId = SchemaMetadata.getInstance().getCollectionMetadata(colName).getSchemaId();
//    		List<IndexMetadata> list = SchemaMetadata.getInstance().getIndexes(colName);
//    		for (int i = 0; i < list.size(); i++) {
//    			IndexMetadata idxMeta = list.get(i);
//    			ResultIterator rs = idxMeta.getIndexTree().getHead(false, pinnedBlocks);
//    			Block currentBlock = rs.getCurrentBlock();
//    			try {
//	    			while (rs.hasNext()) {
//	    				if (rs.getCurrentBlock() != currentBlock) {
//	    					CacheEntryPinner.getInstance().unpin(currentBlock.getPtr(), pinnedBlocks);
//	    					currentBlock = rs.getCurrentBlock();
//	    				}
//	    				IndexResultContent content = (IndexResultContent) rs.next();
//	    				RecordId recId = content.get();
//	    				Set<ColumnType> colList = idxMeta.getIndex().getColumnNameList();
//	    				Iterator<ColumnType> colTypeIter = colList.iterator();
//	    				CacheEntryPinner.getInstance().pin(recId.getPtr(), pinnedBlocks);
//	    				RecordBlock lockedBlock = (RecordBlock) PrimaryCacheHandlerFactory.getInstance().getCacheHandler().get(recId.getPtr());
//	    				
//	    				TableRecord tr = (TableRecord) CacheObjectMgr.getInstance().getRecord(lockedBlock, recId, null, schemaId, null);
//	    				List<DBType> vals = new ArrayList<DBType>();
//	    				while (colTypeIter.hasNext()) {
//	    					ColumnType ct = colTypeIter.next();
//	    					DBType idxVal = content.getValue(ct, null);
//	    					DBType colVal = tr.getColumns().get(ct);
//	    					if (idxVal.compareTo(colVal) != 0) {
//	    						System.out.println("mismatch");
//	    					}
//	    					vals.add(idxVal);
//	    				}
//	    				
//	    				IndexKeyType ikt = new IndexKeyType(vals, recId);
//	    				if (previkt != null) {
//	    					if (previkt.compareTo(ikt) > 0) {
//	    						System.out.println("bad");
//	    					}
//	    				}
//	    				previkt = ikt;
//	    				CacheEntryPinner.getInstance().unpin(pinnedBlocks, pinnedBlocks);
//	    			}
//    			} finally {
//    				rs.unlock();
//    			}
//    			CacheEntryPinner.getInstance().unpin(pinnedBlocks, pinnedBlocks);
//    		}
//    	}
//    }
    
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) {
        e.getCause().printStackTrace();
        
        Channel ch = e.getChannel();
        ch.close();
    }
    
    @Override
    public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e) {
    	WonderDBServer.allServerChannels.add(e.getChannel());
    }    
    
    
    private void handleSelectQuery(DBSelectQuery q, ChannelBuffer buffer, Channel channel) {
    	
		List<List<ResultSetValue>> list = ((DBSelectQuery) q).execute();
		serialize(q, list, buffer, channel);
    }
    
    private void serialize(DBSelectQuery q, List<List<ResultSetValue>> list, ChannelBuffer buffer, Channel channel) {
    	List<ResultSetColumn> rscList = q.resultsetColumns;
    	ChannelBuffer metaBuffer = ChannelBuffers.dynamicBuffer();
    	metaBuffer.writerIndex(5);
    	metaBuffer.writeInt(rscList.size());
    	int colCount = 0;
    	for (int i = 0; i < rscList.size(); i++) {
    		ResultSetColumn rsc = rscList.get(i);
    		String colName = rsc.ca.getCollectionName()+".";
    		if (rsc.ca.getAlias() != null && rsc.ca.getAlias().length() > 0) {
    			colName = rsc.ca.getAlias() + "." + colName;
    		}
    		colName = colName + rsc.cc.getColumnName();
    		String serName = rsc.cc.getCollectionColumnSerializerName();
    		CollectionColumnSerializer<? extends SerializableType> ser = SerializerManager.getInstance().getSerializer(serName);
    		if (ser instanceof BlockPtrSerializer) {
    			continue;
    		}
    		colCount++;
    		metaBuffer.writeInt(colName.getBytes().length);
    		metaBuffer.writeBytes(colName.getBytes());
    		metaBuffer.writeInt(ser.getSQLType());
    	}
    	
    	int posn = metaBuffer.writerIndex();

    	metaBuffer.writerIndex(5);
    	metaBuffer.writeInt(colCount);

    	metaBuffer.writerIndex(0);
    	metaBuffer.writeInt(posn-5);
    	metaBuffer.writeByte(0);
    	metaBuffer.writerIndex(posn);
    	channel.write(metaBuffer);
    	
    	for (int x = 0; x < list.size(); x++) {
    		List<ResultSetValue> valueList = list.get(x);
    		ChannelBuffer tmpBuffer = ChannelBuffers.dynamicBuffer();
    		tmpBuffer.writerIndex(5);
        	for (int i = 0; i < rscList.size(); i++) {
        		ResultSetColumn rsc = rscList.get(i);
//        		int schemaId = SchemaMetadata.getInstance().getCollectionMetadata(rsc.ca.getCollectionName()).getSchemaId();
//        		if (schemaId < 3) {
//        			continue;
//        		}
        		String serName = rsc.cc.getCollectionColumnSerializerName();
        		CollectionColumnSerializer<? extends SerializableType> ser = SerializerManager.getInstance().getSerializer(serName);
        		ResultSetValue rsv = valueList.get(i);
        		
        		if (ser instanceof BlockPtrSerializer) {
        			continue;
        		}
        		if (rsv.value != null) {
        			tmpBuffer.writeByte(0);
    				ser.toBytes(rsv.value, tmpBuffer);
        		} else {
					tmpBuffer.writeByte(1);
				}
    		}
//        	tmpBuffer.clear();
        	int p = tmpBuffer.readableBytes();
        	tmpBuffer.resetWriterIndex();
        	tmpBuffer.writeInt(p-5);
//        	if (x+1 == list.size()) {
//        		tmpBuffer.writeByte(1);
//        	} else {
        		tmpBuffer.writeByte(0);        		
//        	}
			tmpBuffer.writerIndex(p);
			tmpBuffer.resetReaderIndex();
	    	channel.write(tmpBuffer);
//			buffer.clear();
    	}
    	buffer.clear();
    	buffer.writeInt(4);
    	buffer.writeByte((byte) 1);
    	buffer.writeInt(list.size());
    	channel.write(buffer);
//    	buffer.clear();
    }    
    
    private void handleInsertQuery(DBInsertQuery q, ChannelBuffer buffer) {
    	int count = 0;
    	String message = "";
    	int errCode = 0;
    	try {
    		count = q.execute();
    	} catch (InvalidCollectionNameException e) {
    		message = "Collection: " + q.getCollectionName() + " already exists";
    		errCode = 1;
    	} catch (UniqueKeyViolationException e1) {
    		message = "Unique key violation...";
    		errCode = 2;
    	}
//    	String s = count + " record(s) inserted\n";
//    	buffer.writeInt(s.getBytes().length);
//    	buffer.writeByte((byte) 1);
//    	buffer.writeBytes(s.getBytes());

//    	String s = count + " record(s) inserted\n";
    	buffer.clear();
    	buffer.writeInt(8 + message.getBytes().length);
    	buffer.writeByte((byte) 1);
    	buffer.writeInt(count);
    	buffer.writeInt(errCode);
    	buffer.writeBytes(message.getBytes());
    }
    
    private void handleCreateStorageQuery(DBCreateStorageQuery q, ChannelBuffer buffer) {
    	String message = null;
    	int errCode = 0;
    	try {
    		q.execute();
       		message = q.getFileName() + " storage " + q.getFileName() + " created ";
    	} catch (StorageFileCreationException e) {
    		errCode = 5;
    		message = "Cannot create storage: " + q.getFileName();
    	} catch (FileAlreadyExisits e1) {
			message = q.getFileName() + " storage " + q.getFileName() + " already exisits ...";
			errCode = 1;
    	}

    	buffer.clear();
    	buffer.writeInt(8 + message.getBytes().length);
    	buffer.writeByte((byte) 1);
    	buffer.writeInt(1);
    	buffer.writeInt(errCode);
    	buffer.writeBytes(message.getBytes());
    }
    
    private void handleCreateShardQuery(CreateShardQuery q, ChannelBuffer buffer) {
    	q.execute();
    	String message = "shard created ...";
    	buffer.clear();
    	buffer.writeInt(8 + message.getBytes().length);
    	buffer.writeByte((byte) 1);
    	buffer.writeInt(1);
    	buffer.writeInt(0);
    	buffer.writeBytes(message.getBytes());
    }
    
    private void handleCreateReplicaSetQuery(CreateReplicaSetQuery q, ChannelBuffer buffer) {
    	q.execute();
    	String message = "shard created ...";
    	buffer.clear();
    	buffer.writeInt(8 + message.getBytes().length);
    	buffer.writeByte((byte) 1);
    	buffer.writeInt(1);
    	buffer.writeInt(0);
    	buffer.writeBytes(message.getBytes());
    }
    
    private void handleAddToReplicaSetQuery(AddToReplicaSetQuery q, ChannelBuffer buffer) {
    	q.execute();
    	String message = "shard created ...";
    	buffer.clear();
    	buffer.writeInt(8 + message.getBytes().length);
    	buffer.writeByte((byte) 1);
    	buffer.writeInt(1);
    	buffer.writeInt(0);
    	buffer.writeBytes(message.getBytes());
    }
    
    private void handleUpdateQuery(DBUpdateQuery q, ChannelBuffer buffer) {
    	int count = 0;
    	String message = "";
    	int errCode = 0;
    	
    	try {
    		ScatterGatherQueryExecutor sgqe = new ScatterGatherQueryExecutor(q);
    		count = sgqe.updateQuery();
    	} catch (UniqueKeyViolationException e1) {
    		message = "Unique key violation...";
    		errCode = 2;
    	}
//    	String s = count + " records updated\n";
    	buffer.clear();
    	buffer.writeInt(8+message.getBytes().length);
    	buffer.writeByte((byte) 1);
    	buffer.writeInt(count);
    	buffer.writeInt(errCode);
    	buffer.writeBytes(message.getBytes());
    }
    
    private void handleDeleteQuery(DBDeleteQuery q, ChannelBuffer buffer) {
    	int count = 0;
    	ScatterGatherQueryExecutor sgqe = new ScatterGatherQueryExecutor(q);
   		count = sgqe.deleteQuery();
    	buffer.clear();
    	buffer.writeInt(8);
    	buffer.writeByte((byte) 1);
    	buffer.writeInt(count);
    	buffer.writeInt(0);
    }
    
    private void handleCreateIndexQuery(CreateIndexQuery q, ChannelBuffer buffer) {
    	int errCode = 0;
    	String message = "";
    	try {
    		q.execute();
    		message = "Index " + q.getIndexName() + " created";
    	} catch (InvalidCollectionNameException e) {
    		errCode = 1;
    		message = "Table " + q.getCollectionName() + " already exisits ";
    	} catch (InvalidIndexException e1) {
    		errCode = 2;
    		message = "Index " + q.getIndexName() + " already exists";
    	}
    	buffer.writeInt(8 + message.getBytes().length);
    	buffer.writeByte((byte) 1);
    	buffer.writeInt(1);
    	buffer.writeInt(errCode);
    	buffer.writeBytes(message.getBytes());
    }
    
    private void handleCreateTableQuery(CreateTableQuery q, ChannelBuffer buffer) {
    	String message = "";
    	int errCode = 0;
    	try {
    		q.execute();
    		message = "Table " + q.getCollectionName() + " created";
    	} catch (InvalidCollectionNameException e) {
    		message = "Collection: " + q.getCollectionName() + " already exists";
    		errCode = 1;
    	}
    	buffer.writeInt(8 + message.getBytes().length);
    	buffer.writeByte((byte) 1);
    	buffer.writeInt(1);
    	buffer.writeInt(errCode);
    	buffer.writeBytes(message.getBytes());
    }
    
    private void handleExplainPlan(DBShowPlanQuery query, ChannelBuffer buffer, Channel channel) {
    	List<QueryPlan> list = query.execute();
    	StringBuilder builder = new StringBuilder();
    	
    	for (QueryPlan plan : list) {
    		if (plan instanceof FullTableScan) {
    			builder.append("Full Collection Scan on ")
    			.append(plan.getCollectionAlias().getAlias());

    			String alias = plan.getCollectionAlias().getAlias();
    			if (alias != null && alias.length() > 0) {
    				builder.append(".");
    			}
    			builder.append(plan.getCollectionAlias().getCollectionName()).append('\n');
    		}
    		if (plan instanceof IndexRangeScan) {
    			builder.append("Index Scan on ").append(((IndexRangeScan) plan).getIndex().getIndexName()).append('\n');
    		}
    	}
    	serializeExplainPlan(builder.toString(), channel);
    }
    
    private void serializeExplainPlan(String plan, Channel channel) {
    	ChannelBuffer metaBuffer = ChannelBuffers.dynamicBuffer();
    	metaBuffer.writerIndex(5);
    	metaBuffer.writeInt(1);
		String colName = "";
		metaBuffer.writeInt(colName.getBytes().length);
		metaBuffer.writeBytes(colName.getBytes());
		metaBuffer.writeInt(Types.VARCHAR);
    	
    	int posn = metaBuffer.writerIndex();
    	metaBuffer.writerIndex(0);
    	metaBuffer.writeInt(posn-5);
    	metaBuffer.writeByte(0);
    	metaBuffer.writerIndex(posn);
    	channel.write(metaBuffer);

		ChannelBuffer tmpBuffer = ChannelBuffers.dynamicBuffer();
		tmpBuffer.writerIndex(5);
    		
		CollectionColumnSerializer<? extends SerializableType> ser = StringSerializer.getInstance();
		tmpBuffer.writeByte(0);
		ser.toBytes(new StringType(plan), tmpBuffer);

    	int p = tmpBuffer.readableBytes();
    	tmpBuffer.resetWriterIndex();
    	tmpBuffer.writeInt(p-5);
   		tmpBuffer.writeByte(0);        		
		tmpBuffer.writerIndex(p);
		tmpBuffer.resetReaderIndex();
    	channel.write(tmpBuffer);

	    tmpBuffer.clear();
	    tmpBuffer.writeInt(4);
	    tmpBuffer.writeByte((byte) 1);
	    tmpBuffer.writeInt(1);
    	channel.write(tmpBuffer);
    }
    
    private void handleShowTableQuery(ShowTableQuery q, Channel channel) {
    	String s = q.execute();
    	serializeExplainPlan(s, channel);
    }
    
    private void handleShowStoragesQuery(ShowStoragesQuery q, Channel channel) {
    	String s = q.execute();
    	serializeExplainPlan(s, channel);
    }
    
    private void handleShowIndexQuery(ShowIndexQuery q, Channel channel) {
    	String s = q.execute();
    	serializeExplainPlan(s, channel);
    }
    
    private void handleShowSchemaQuery(ShowSchemaQuery q, Channel channel) {
    	String s = q.execute();
    	serializeExplainPlan(s, channel);
    }
    
}