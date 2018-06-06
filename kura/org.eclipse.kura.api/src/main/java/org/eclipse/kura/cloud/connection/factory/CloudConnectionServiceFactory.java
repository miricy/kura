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
import org.eclipse.kura.cloud.connection.CloudConnectionService;
import org.osgi.annotation.versioning.ProviderType;

/**
 * A {@link CloudConnectionServiceFactory} represents an OSGi Declarative Service Component
 * that registers {@link CloudConnectionService}s in the framework.
 * The Component creates multiple component instances upon reception of a configuration
 * created through the Configuration Service.
 *
 * It provides a {@link CloudConnectionService} implementation that can be used to connect to a specific Cloud platform.
 *
 * Typically, each {@link CloudConnectionService} created by a {@link CloudConnectionServiceFactory}
 * establishes and manages its own connection, for example an Mqtt connection.
 *
 * Multiple CloudServiceFactory services can be registered in the framework to support multiple simultaneous
 * connections to different Cloud platforms.
 *
 * A CloudServiceFactory manages the construction of a CloudService and the services it depends on.
 *
 * @noimplement This interface is not intended to be implemented by clients.
 * @since 2.0
 */
@ProviderType
public interface CloudConnectionServiceFactory {

    /**
     * The name of the property set in a @{link CloudConnectionService} configuration created
     * through {@link #createConfiguration}.
     * The property is set in the {@link CloudConnectionService} instance to relate it with the Factory that generated
     * the whole cloud stack.
     */
    public static final String KURA_CLOUD_CONNECTION_SERVICE_FACTORY_PID = "kura.cloud.connection.service.factory.pid";

    /**
     * Returns the factory PID of the OSGi Factory Component represented by this {@link CloudConnectionService}.
     *
     * @return a String representing the factory PID of the Factory Component.
     */
    public String getFactoryPid();

    /**
     * Creates a {@link CloudConnectionService} instance and initializes its configuration with the defaults
     * expressed in the Metatype of the target component factory providing the {@link CloudConnectionService}.
     *
     * The created {@link CloudConnectionService} instance will have its {@code kura.service.pid} property
     * set to the value provided in the {@code pid} parameter.
     *
     * @param pid
     *            the Kura persistent identifier ({@code kura.service.pid}) of the {@link CloudConnectionService}
     *            instance created by this factory.
     * @throws KuraException
     *             an exception is thrown in case the creation operation fails
     */
    public void createConfiguration(String pid) throws KuraException;

    /**
     * Returns the list of {@code kura.service.pid}s that compose the cloud stack associated with the provided
     * {@code kura.service.pid} associated to a {@link CloudConnectionService} instance.
     *
     * @param pid
     *            the Kura persistent identifier, {@code kura.service.pid}, of a {@link CloudConnectionService}
     *            instance.
     * @return the {@link List} of {@code kura.service.pid}s related to the provided {@code pid}.
     * @throws KuraException
     *             if the specified {@code kura.service.pid} is incorrect.
     */
    public List<String> getStackComponentsPids(String pid) throws KuraException;

    /**
     * Deletes a previously created configuration deactivating the associated {@link CloudConnectionService} instance.
     *
     * @param pid
     *            the Kura persistent identifier, {@code kura.service.pid}, of the {@link CloudConnectionService}
     *            instance.
     * @throws KuraException
     *             if the provided {@code kura.service.pid} is incorrect or the delete operation fails.
     */
    public void deleteConfiguration(String pid) throws KuraException;

    /**
     * Returns a set of services managed by this factory
     * It is up to the factory to specify how to assembles the result. The PIDs returned by this list must be the PIDs
     * assigned
     * to the OSGi service property {@code kura.service.pid} and it must be possible to pass all those results into
     * the method {@link #getStackComponentsPids(String)} of the same factory instance.
     *
     * The IDs returned by this method must not necessarily point to registered OSGi services. But if they do, they must
     * point only to instances of {@link CloudConnectionService}.
     *
     * @return the set of services, never returns {@code null}
     * @throws KuraException
     */
    public Set<String> getManagedCloudServicePids() throws KuraException;

}
