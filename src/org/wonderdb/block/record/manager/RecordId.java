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
package org.wonderdb.block.record.manager;

import org.wonderdb.block.BlockPtr;

public class RecordId implements Comparable<RecordId> {
	BlockPtr ptr;
	int posn;
	
	public RecordId(BlockPtr ptr, int posn) {
		this.ptr = ptr;
		this.posn = posn;
	}
	
	public boolean equals(Object o) {
		RecordId id = null;
		if (o instanceof RecordId) {
			id = (RecordId) o;
		}
		
		if (id == null) {
			return false;
		}
		return compareTo(id) == 0 ? true : false;
	}
	
	public int compareTo(RecordId recordId) {
		int c = ptr.compareTo(recordId.getPtr());
		if (c == 0) {
			return posn > recordId.getPosn() ? 1 : posn < recordId.getPosn() ? -1 : 0;
		}
		
		return c;
	}

	public int hashCode() {
		return this.ptr.hashCode() + posn;
	}
	
	public int getPosn() {
		return posn;
	}
	
	public BlockPtr getPtr() {
		return ptr;
	}
	
	public String toString() {
		return ptr.toString() + " " + posn;
	}
}
