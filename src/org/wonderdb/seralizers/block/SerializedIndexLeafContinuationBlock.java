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
package org.wonderdb.seralizers.block;

import java.util.Set;

import org.wonderdb.block.BlockPtr;


public class SerializedIndexLeafContinuationBlock extends SerializedContinuationBlock implements SerializedIndexBlock, SerializedLeafBlock {
//	public static final int HEADER_SIZE = SerializedContinuationBlock.HEADER_SIZE + 3*BlockPtrSerializer.BASE_SIZE;

	public SerializedIndexLeafContinuationBlock(SerializedBlock block, Set<BlockPtr> pinnedBlocks, boolean newBlock, boolean update) {
		super(block, pinnedBlocks, newBlock, update);
//		this.setNextPtr(new SingleBlockPtr((byte) -1, 0));
//		this.setPrevPtr(new SingleBlockPtr((byte) -1, 0));
//		this.setParentPtr(new SingleBlockPtr((byte) -1, 0));
	}
	
	public SerializedIndexLeafContinuationBlock(SerializedBlock block, Set<BlockPtr> pinnedBlocks, boolean update) {
		super(block, pinnedBlocks, false, update);
	}
	
	@Override
	public BlockPtr getParentPtr() {
		return null;
	}
	
	
	@Override 
	public void setParentPtr(BlockPtr p) {
	}
	
//	@Override
//	public ChannelBuffer getDataBuffer() {
//		return super.getDataBuffer();
//	}
//	
	@Override
	public BlockPtr getNextPtr() {
		return null;
	}
	
	@Override 
	public void setNextPtr(BlockPtr p) {
	}

	@Override
	public BlockPtr getPrevPtr() {
		return null;
	}

	@Override
	public void setPrevPtr(BlockPtr p) {
	}
}
