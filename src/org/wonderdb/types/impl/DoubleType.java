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
package org.wonderdb.types.impl;

import org.wonderdb.seralizers.DoubleSerializer;
import org.wonderdb.types.DBType;

public class DoubleType implements DBType {
	Double value;
	
	public int getByteSize() {
		return DoubleSerializer.getInstance().getByteSize(this);
	}

	public DoubleType(Double v) {
		value = v;
	}
	
	public Double get() {
		return value;
	}
	
	public DoubleType copyOf() {
		return new DoubleType(value);
	}
	
	public String getSerializerName() {
		return "ds";
	}
	
	public void set(String s) {
		try {
			value = Double.valueOf(s).doubleValue();
		} catch (NumberFormatException e) {
			e.printStackTrace();
		}
	}
	
	public int compareTo(DBType t) {
		DoubleType dt = null;
		if (t instanceof DoubleType) {
			dt = (DoubleType) t;
		} else {
			return -1;
		}
		
		if (this.value != null) {
			return this.value.compareTo(dt.value);
		} else if (dt.value != null) {
			return -1;
		}
		return 0;
	}
	
	public boolean equals(Object o) {
		if (o instanceof DoubleType) {
			return this.compareTo((DoubleType) o) == 0;
		}
		return false;
	}

	public String toString() {
		return String.valueOf(value);
	}
	
	public long getBlockPosn() {
		throw new RuntimeException("Method not supported");		
	}
	
	public int getPosn() {
		throw new RuntimeException("Method not supported");
	}

}
