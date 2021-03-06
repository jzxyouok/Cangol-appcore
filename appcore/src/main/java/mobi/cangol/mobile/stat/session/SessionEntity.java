/**
 * Copyright (c) 2013 Cangol
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package mobi.cangol.mobile.stat.session;

/**
 * Created by weixuewu on 16/1/23.
 */
class SessionEntity  implements Cloneable{
	String sessionId;
	long beginSession;
	long sessionDuration;
	long endSession;
	String activityId;
	@Override
	protected Object clone() throws CloneNotSupportedException {
		Object o = null;
		try {
			o = (SessionEntity) super.clone();
		} catch (CloneNotSupportedException e) {
			e.printStackTrace();
		}
		return o;
	}

}
