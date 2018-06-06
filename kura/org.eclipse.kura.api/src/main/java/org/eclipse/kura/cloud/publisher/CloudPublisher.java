/*******************************************************************************
 * Copyright (c) 2018 Eurotech and/or its affiliates and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 *******************************************************************************/
package org.eclipse.kura.cloud.publisher;

import org.eclipse.kura.KuraException;
import org.eclipse.kura.cloud.connection.CloudConnectionListenerTracker;
import org.eclipse.kura.cloud.connection.CloudConnectionService;
import org.eclipse.kura.message.KuraPayload;
import org.osgi.annotation.versioning.ProviderType;

/**
 * Interface intended to have a specific implementation associated to a {@link CloudConnectionService} and with
 * specificities related to the targeted cloud provider.
 * The {@link CloudPublisher} interface is an abstraction on top of the {@link CloudConnectionService} to simplify the
 * publishing process for each application running in the framework.
 * When an application wants to publish, it has to take a {@link CloudPublisher} instance and use the
 * {@link CloudPublisher#publish(KuraPayload)} method, passing as argument a {@link KuraPayload} message.
 *
 * @since 2.0
 * @noimplement This interface is not intended to be implemented by clients.
 */
@ProviderType
public interface CloudPublisher extends CloudConnectionListenerTracker {

    /**
     * Publishes the received {@link KuraPayload} message using the associated Cloud Stack.
     *
     * @param message
     *            The message to be published
     * @return an integer representing the message ID
     * @throws KuraException
     *             if the publishing operation fails.
     */
    public int publish(KuraPayload message) throws KuraException;

}
