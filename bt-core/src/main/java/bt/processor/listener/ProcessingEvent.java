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

package bt.processor.listener;

/**
 * Significant milestone in torrent processing.
 *
 * @since 1.5
 */
public enum ProcessingEvent {

    /**
     * Torrent metadata has been fetched.
     *
     * @since 1.5
     */
    TORRENT_FETCHED,

    /**
     * All data has been downloaded.
     *
     * @since 1.5
     */
    DOWNLOAD_COMPLETE
}
