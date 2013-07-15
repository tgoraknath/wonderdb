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
package org.wonderdb.block.impl.disk;

import org.wonderdb.block.BlockPtr;
import org.wonderdb.block.record.impl.base.BaseRecordBlock;

public class DiskRecordBlock extends BaseRecordBlock {
	public DiskRecordBlock(int schemaObjectId, BlockPtr ptr) {
		super(ptr, schemaObjectId, 0);
	}

	public String getSerializerName() {
		return "LB";
	}	
}