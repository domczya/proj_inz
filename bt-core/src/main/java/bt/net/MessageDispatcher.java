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

package bt.net;

import bt.protocol.Message;
import bt.runtime.Config;
import bt.service.IRuntimeLifecycleBinder;
import bt.torrent.TorrentRegistry;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Default single-threaded message dispatcher implementation.
 *
 *<p><b>Note that this class implements a service.
 * Hence, is not a part of the public API and is a subject to change.</b></p>
 */
public class MessageDispatcher implements IMessageDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageDispatcher.class);

    private final Map<Peer, Collection<Consumer<Message>>> consumers;
    private final Map<Peer, Collection<Supplier<Message>>> suppliers;

    private TorrentRegistry torrentRegistry;

    @Inject
    public MessageDispatcher(IRuntimeLifecycleBinder lifecycleBinder,
                             IPeerConnectionPool pool,
                             TorrentRegistry torrentRegistry,
                             Config config) {

        this.consumers = new ConcurrentHashMap<>();
        this.suppliers = new ConcurrentHashMap<>();
        this.torrentRegistry = torrentRegistry;

        initializeMessageLoop(lifecycleBinder, pool, config);
    }

    private void initializeMessageLoop(IRuntimeLifecycleBinder lifecycleBinder,
                                       IPeerConnectionPool pool,
                                       Config config) {
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "bt.net.message-dispatcher"));
        LoopControl loopControl = new LoopControl(config.getMaxMessageProcessingInterval().toMillis());
        MessageDispatchingLoop loop = new MessageDispatchingLoop(pool, loopControl);
        lifecycleBinder.onStartup("Initialize message dispatcher", () -> executor.execute(loop));
        lifecycleBinder.onShutdown("Shutdown message dispatcher", () -> {
            try {
                loop.shutdown();
            } finally {
                executor.shutdownNow();
            }
        });
    }


    private class MessageDispatchingLoop implements Runnable {

        private final IPeerConnectionPool pool;
        private final LoopControl loopControl;

        private volatile boolean shutdown;

        MessageDispatchingLoop(IPeerConnectionPool pool, LoopControl loopControl) {
            this.pool = pool;
            this.loopControl = loopControl;
        }

        @Override
        public void run() {
            while (!shutdown) {
                if (!consumers.isEmpty()) {
                    outer:
                    for (Map.Entry<Peer, Collection<Consumer<Message>>> entry : consumers.entrySet()) {
                        Peer peer = entry.getKey();
                        Collection<Consumer<Message>> consumers = entry.getValue();

                        PeerConnection connection = pool.getConnection(peer);
                        if (connection != null && !connection.isClosed()) {
                            if (torrentRegistry.isSupportedAndActive(connection.getTorrentId())) {
                                Message message;
                                for (;;) {
                                    try {
                                        message = connection.readMessageNow();
                                    } catch (Exception e) {
                                        LOGGER.error("Error when reading message from peer connection: " + peer, e);
                                        continue outer;
                                    }

                                    if (message != null) {
                                        loopControl.incrementProcessed();
                                        for (Consumer<Message> consumer : consumers) {
                                            try {
                                                consumer.accept(message);
                                            } catch (Exception e) {
                                                LOGGER.warn("Error in message consumer", e);
                                            }
                                        }
                                    } else {
                                        continue outer;
                                    }
                                }
                            }
                        }
                    }
                }

                if (!suppliers.isEmpty()) {
                    for (Map.Entry<Peer, Collection<Supplier<Message>>> entry : suppliers.entrySet()) {
                        Peer peer = entry.getKey();
                        Collection<Supplier<Message>> suppliers = entry.getValue();

                        PeerConnection connection = pool.getConnection(peer);
                        if (connection != null && !connection.isClosed()) {
                            if (torrentRegistry.isSupportedAndActive(connection.getTorrentId())) {
                                for (Supplier<Message> messageSupplier : suppliers) {
                                    Message message = null;
                                    try {
                                        message = messageSupplier.get();
                                    } catch (Exception e) {
                                        LOGGER.warn("Error in message supplier", e);
                                    }

                                    if (message != null) {
                                        loopControl.incrementProcessed();
                                        try {
                                            connection.postMessage(message);
                                        } catch (Exception e) {
                                            LOGGER.error("Error when writing message", e);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                loopControl.iterationFinished();
            }
        }

        public void shutdown() {
            shutdown = true;
        }
    }

    /**
     * Controls the amount of time to sleep after each iteration of the main message processing loop.
     * It implements an adaptive strategy and increases the amount of time for the dispatcher to sleep
     * after each iteration during which no messages were either received or sent.
     * This strategy greatly reduces CPU load when there is little network activity.
     */
    private static class LoopControl {

        private long maxTimeToSleep;
        private int messagesProcessed;
        private long timeToSleep;

        LoopControl(long maxTimeToSleep) {
            this.maxTimeToSleep = maxTimeToSleep;
            reset();
        }

        private void reset() {
            messagesProcessed = 0;
            timeToSleep = 1;
        }

        void incrementProcessed() {
            messagesProcessed++;
        }

        synchronized void iterationFinished() {
            if (messagesProcessed > 0) {
                reset();
            } else {
                try {
                    wait(timeToSleep);
                } catch (InterruptedException e) {
                    throw new RuntimeException("Unexpectedly interrupted", e);
                }

                if (timeToSleep < maxTimeToSleep) {
                    timeToSleep = Math.min(timeToSleep << 1, maxTimeToSleep);
                } else {
                    timeToSleep = maxTimeToSleep;
                }
            }
        }
    }

    @Override
    public synchronized void addMessageConsumer(Peer sender, Consumer<Message> messageConsumer) {
        Collection<Consumer<Message>> peerConsumers = consumers.get(sender);
        if (peerConsumers == null) {
            peerConsumers = ConcurrentHashMap.newKeySet();
            consumers.put(sender, peerConsumers);
        }
        peerConsumers.add(messageConsumer);
    }

    @Override
    public synchronized void addMessageSupplier(Peer recipient, Supplier<Message> messageSupplier) {
        Collection<Supplier<Message>> peerSuppliers = suppliers.get(recipient);
        if (peerSuppliers == null) {
            peerSuppliers = ConcurrentHashMap.newKeySet();
            suppliers.put(recipient, peerSuppliers);
        }
        peerSuppliers.add(messageSupplier);
    }
}
