/*******************************************************************************
 * Copyright (c) 2018 Eurotech and/or its affiliates
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Eurotech
 *******************************************************************************/
package org.eclipse.kura.cloud.connection;

import java.util.Collections;
import java.util.Map;

import org.eclipse.kura.KuraException;
import org.eclipse.kura.cloud.publisher.CloudPublisher;
import org.eclipse.kura.cloud.subscriber.CloudSubscriber;
import org.osgi.annotation.versioning.ProviderType;

/**
 * The {@link CloudConnectionService} provides an API layer to ease the communication with a remote server.
 * {@link CloudConnectionService} abstracts the developers from the complexity of the transport protocol and payload
 * format used in the communication.
 * It allows for a single connection to a remote server to be shared across more than one {@link CloudPublisher} and
 * {@link CloudSubscriber} running in the gateway.
 *
 * Each cloud provider should provide its own {@link CloudConnectionService} implementation in order to best fit the
 * specific requirements.
 *
 * Applications should not use directly this API but, instead, use the {@link CloudPublisher} and the
 * {@link CloudSubscriber} interfaces to give applications the capabilities to publish and receive messages.
 *
 * @noimplement This interface is not intended to be implemented by clients.
 * @since 2.0
 */
@ProviderType
public interface CloudConnectionService extends CloudConnectionListenerTracker {

    /**
     * Connects the framework to the remote cloud server.
     *
     * @throws KuraException
     *             if the connection operation fails
     */
    public void connect() throws KuraException;

    /**
     * Performs a safe disconnection from the remote server.
     *
     * @throws KuraException
     *             if the operation fails
     */
    public void disconnect() throws KuraException;

    /**
     * Returns true if the framework is currently connected to the remote server. The default implementation return
     * {@code false}
     *
     * @return {@code true} if the framework is connected to the remote server. {@code false} otherwise.
     */
    public default boolean isConnected() {
        return false;
    }

    /**
     * Provides information relative to the associated connection. The information provided depends on the specific
     * implementation and type of connection to the remote resource. The default implementation returns an empty
     * {@link Map}
     *
     * @return a {@link Map} that represents all the information related to the specific connection.
     */
    public default Map<String, String> getConnectionInfo() {
        return Collections.emptyMap();
    }
}
