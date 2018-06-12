package org.eclipse.kura.cloud.connection;

import org.eclipse.kura.cloud.connection.factory.CloudConnectionServiceFactory;

/**
 * @since 2.0
 */
public enum Constants {

    /**
     * The key of the property that specifies the {@code kura.service.pid} of the associated
     * {@link CloudConnectionService}
     * in {@code CloudPublisher} or {@code CloudSubscriber} component configuration.
     */
    CLOUD_CONNECTION_SERVICE_PID_PROP_NAME("cloud.connection.service.pid"),

    /**
     * The key of the property that specifies the {@code kura.service.pid} of the associated
     * {@link CloudConnectionServiceFactory}
     * in {@link CloudConnectionService} component definition.
     */
    CLOUD_CONNECTION_SERVICE_FACTORY_PID_PROP_NAME("cloud.connection.service.factory.pid");

    String value;

    Constants(final String value) {
        this.value = value;
    }

    public String value() {
        return this.value;
    }
}
