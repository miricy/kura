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

import java.util.HashMap;
import java.util.Map;

import org.eclipse.kura.KuraException;

/**
 * @since 2.0
 */
public interface CloudConnectionService extends CloudConnectionListenerTracker {

    public void connect() throws KuraException;

    public void disconnect() throws KuraException;

    public default boolean isConnected() {
        return false;
    }

    public default Map<String, String> getConnectionInfo() {
        return new HashMap<>();
    }
}
