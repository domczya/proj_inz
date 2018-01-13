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

package bt.torrent;

import bt.net.Peer;
import bt.torrent.messaging.ConnectionState;
import bt.torrent.messaging.TorrentWorker;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class DefaultTorrentSessionState implements TorrentSessionState {

    private static final int DOWNLOADED_POSITION = 0;
    private static final int UPLOADED_POSITION = 1;

    /**
     * Recently calculated amounts of downloaded and uploaded data
     */
    private Map<Peer, Long[]> recentAmountsForConnectedPeers;

    /**
     * Historical data (amount of data downloaded from disconnected peers)
     */
    private volatile AtomicLong downloadedFromDisconnected;

    /**
     * Historical data (amount of data uploaded to disconnected peers)
     */
    private volatile AtomicLong uploadedToDisconnected;

    private final TorrentDescriptor descriptor;
    private final TorrentWorker worker;

    public DefaultTorrentSessionState(TorrentDescriptor descriptor, TorrentWorker worker) {
        this.recentAmountsForConnectedPeers = new HashMap<>();
        this.downloadedFromDisconnected = new AtomicLong();
        this.uploadedToDisconnected = new AtomicLong();
        this.descriptor = descriptor;
        this.worker = worker;
    }

    @Override
    public int getPiecesTotal() {
        if (descriptor.getDataDescriptor() != null) {
            return descriptor.getDataDescriptor().getBitfield().getPiecesTotal();
        } else {
            return 1;
        }
    }

    @Override
    public int getPiecesRemaining() {
        if (descriptor.getDataDescriptor() != null) {
            return descriptor.getDataDescriptor().getBitfield().getPiecesRemaining();
        } else {
            return 1;
        }
    }

    @Override
    public synchronized long getDownloaded() {
        long downloaded = getCurrentAmounts().values().stream()
                .collect(Collectors.summingLong(amounts -> amounts[DOWNLOADED_POSITION]));
        downloaded += downloadedFromDisconnected.get();
        return downloaded;
    }

    @Override
    public synchronized long getUploaded() {
        long uploaded = getCurrentAmounts().values().stream()
                .collect(Collectors.summingLong(amounts -> amounts[UPLOADED_POSITION]));
        uploaded += uploadedToDisconnected.get();
        return uploaded;
    }

    private synchronized Map<Peer, Long[]> getCurrentAmounts() {
        Map<Peer, Long[]> connectedPeers = getAmountsForConnectedPeers();
        connectedPeers.forEach((peer, amounts) -> recentAmountsForConnectedPeers.put(peer, amounts));

        Set<Peer> disconnectedPeers = new HashSet<>();
        recentAmountsForConnectedPeers.forEach((peer, amounts) -> {
            if (!connectedPeers.containsKey(peer)) {
                downloadedFromDisconnected.addAndGet(amounts[DOWNLOADED_POSITION]);
                uploadedToDisconnected.addAndGet(amounts[UPLOADED_POSITION]);
                disconnectedPeers.add(peer);
            }
        });
        disconnectedPeers.forEach(recentAmountsForConnectedPeers::remove);

        return recentAmountsForConnectedPeers;
    }

    private Map<Peer, Long[]> getAmountsForConnectedPeers() {
        return worker.getPeers().stream()
                .collect(
                        HashMap::new,
                        (acc, peer) -> {
                            ConnectionState connectionState = worker.getConnectionState(peer);
                            acc.put(peer, new Long[] {connectionState.getDownloaded(), connectionState.getUploaded()});
                        },
                        HashMap::putAll);
    }

    @Override
    public Set<Peer> getConnectedPeers() {
        return Collections.unmodifiableSet(worker.getPeers());
    }
}
