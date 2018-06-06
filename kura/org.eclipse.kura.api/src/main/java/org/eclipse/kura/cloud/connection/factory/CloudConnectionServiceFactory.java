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
package org.eclipse.kura.cloud.connection.factory;

import java.util.List;
import java.util.Set;

import org.eclipse.kura.KuraException;

/**
 * @since 2.0
 */
public interface CloudConnectionServiceFactory {

    public static final String KURA_CLOUD_CONNECTION_SERVICE_FACTORY_PID = "kura.cloud.connection.service.factory.pid";

    public String getFactoryPid();

    public void createConfiguration(String pid) throws KuraException;

    public List<String> getStackComponentsPids(String pid) throws KuraException;

    public void deleteConfiguration(String pid) throws KuraException;

    public Set<String> getManagedCloudServicePids() throws KuraException;

}
