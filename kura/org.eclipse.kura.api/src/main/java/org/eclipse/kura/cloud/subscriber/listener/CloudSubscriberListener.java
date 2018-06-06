/*******************************************************************************
 * Copyright (c) 2018 Eurotech and/or its affiliates and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 *******************************************************************************/
package org.eclipse.kura.cloud.subscriber.listener;

import java.util.Map;

import org.eclipse.kura.cloud.subscriber.CloudSubscriber;
import org.eclipse.kura.message.KuraPayload;
import org.osgi.annotation.versioning.ConsumerType;

/**
 * {@link CloudSubscriberListener} is the interface to be implemented by applications that needs to be notified of
 * events in the subscriber.
 * Arrived methods are invoked whenever a message is received in the associated {@link CloudSubscriber}.
 * 
 * @since 2.0
 */
@ConsumerType
public interface CloudSubscriberListener {

    /**
     * Called by the {@link CloudSubscriber} when a message is received from the broker.
     * The received message will be parsed and passed as a {@link KuraPayload} to the listener.
     * A set of properties is also passed as a {@link Map}.
     *
     * @param properties
     *            a {@link Map} that provides additional properties to the listener. Those properties can be filled in
     *            different ways depending on the subscriber implementation.
     * @param payload
     *            The {@link KuraPayload} that wraps the received message.
     */
    public void onMessageArrived(Map<String, Object> properties, KuraPayload payload);
}
