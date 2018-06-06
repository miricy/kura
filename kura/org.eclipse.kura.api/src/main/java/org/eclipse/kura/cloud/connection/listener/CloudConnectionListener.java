/*******************************************************************************
 * Copyright (c) 2018 Eurotech and/or its affiliates and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 *******************************************************************************/
package org.eclipse.kura.cloud.connection.listener;

import org.osgi.annotation.versioning.ConsumerType;

/**
 * @since 2.0
 */
@ConsumerType
public interface CloudConnectionListener {

    public void onDisconnected();

    public void onConnectionLost();

    public void onConnectionEstablished();

}
