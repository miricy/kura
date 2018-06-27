/*******************************************************************************
 * Copyright (c) 2018 Eurotech and/or its affiliates and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 *******************************************************************************/
package org.eclipse.kura.cloudconnection.listener;

import org.eclipse.kura.cloudconnection.CloudConnectionListenerRegistry;
import org.osgi.annotation.versioning.ConsumerType;

/**
 * {@link CloudConnectionListener} interface is implemented by applications that want to be notified on connection
 * status changes.
 * To be notified, the implementor need to register itself to a specific {@link CloudConnectionListenerRegistry}.
 *
 * @since 2.0
 */
@ConsumerType
public interface CloudConnectionListener {

    /**
     * Notifies that the cloud stack has cleanly disconnected.
     */
    public void onDisconnected();

    /**
     * Called when the client has lost its connection with the broker. This is only a notification, the callback handler
     * should
     * not attempt to handle the reconnect.
     *
     * If the bundle using the client relies on subscriptions beyond the default ones,
     * it is responsibility of the application to implement the {@link CloudConnectionListener#onConnectionEstablished}
     * callback method to restore the subscriptions it needs after a connection loss.
     */
    public void onConnectionLost();

    /**
     * Called when the cloud stack has successfully connected to the broker.
     * <br>
     * If the bundle using the client relies on subscriptions beyond the default ones,
     * it is responsibility of the application to implement the {@link CloudConnectionListener#onConnectionEstablished}
     * callback method to restore the subscriptions it needs after a connection loss.
     */
    public void onConnectionEstablished();

}
