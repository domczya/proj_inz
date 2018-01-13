/*
 * Copyright (c) 2016—2017 Andrei Tomashpolskiy and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package bt.torrent.compiler;

import bt.protocol.Message;

import java.lang.invoke.MethodHandle;

class ConsumerInfo {

    private MethodHandle handle;
    private Class<? extends Message> consumedMessageType;

    public MethodHandle getHandle() {
        return handle;
    }

    public void setHandle(MethodHandle handle) {
        this.handle = handle;
    }

    public Class<? extends Message> getConsumedMessageType() {
        return consumedMessageType;
    }

    public void setConsumedMessageType(Class<? extends Message> consumedMessageType) {
        this.consumedMessageType = consumedMessageType;
    }
}
