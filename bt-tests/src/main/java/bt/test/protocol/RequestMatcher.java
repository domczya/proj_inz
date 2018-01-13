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

package bt.test.protocol;

import bt.protocol.Request;

import java.util.function.BiPredicate;

import static org.junit.Assert.assertEquals;

final class RequestMatcher implements BiPredicate<Request, Request> {

    @Override
    public boolean test(Request request, Request request2) {
        assertEquals(request.getPieceIndex(), request2.getPieceIndex());
        assertEquals(request.getOffset(), request2.getOffset());
        assertEquals(request.getLength(), request2.getLength());
        return true;
    }
}
