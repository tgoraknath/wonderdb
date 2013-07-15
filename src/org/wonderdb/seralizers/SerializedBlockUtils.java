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
package org.wonderdb.seralizers;

import org.jboss.netty.buffer.ChannelBuffer;
import org.wonderdb.schema.StorageUtils;


public class SerializedBlockUtils {
	private static SerializedBlockUtils instance = new SerializedBlockUtils();
	
	private SerializedBlockUtils() {
	}
	
	public static SerializedBlockUtils getInstance() {
		return instance;
	}
	
	public ChannelBuffer[] getBuffers(ChannelBuffer buffer) {
		int blockSize = StorageUtils.getInstance().getSmallestBlockSize();
		int size = blockSize;
		ChannelBuffer[] retVal = new ChannelBuffer[buffer.capacity()/blockSize]; 
		for (int i = 0; i < retVal.length; i++) {
			ChannelBuffer cb = buffer.slice(size-blockSize, blockSize);
			size = size + blockSize;
			retVal[i] = cb;
		}
		return retVal;
	}
}
