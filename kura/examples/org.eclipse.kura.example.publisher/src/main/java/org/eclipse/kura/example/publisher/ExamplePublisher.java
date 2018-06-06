/*******************************************************************************
 * Copyright (c) 2011, 2018 Eurotech and/or its affiliates and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Eurotech and/or its affiliates
 *     Red Hat Inc
 *******************************************************************************/
package org.eclipse.kura.example.publisher;

import static java.util.Objects.nonNull;

import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.kura.cloud.connection.listener.CloudConnectionListener;
import org.eclipse.kura.cloud.publisher.CloudPublisher;
import org.eclipse.kura.cloud.subscriber.CloudSubscriber;
import org.eclipse.kura.cloud.subscriber.listener.CloudSubscriberListener;
import org.eclipse.kura.configuration.ConfigurableComponent;
import org.eclipse.kura.message.KuraPayload;
import org.eclipse.kura.message.KuraPosition;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExamplePublisher implements ConfigurableComponent, CloudSubscriberListener, CloudConnectionListener {

    /**
     * Inner class defined to track the CloudServices as they get added, modified or removed.
     * Specific methods can refresh the cloudService definition and setup again the Cloud Client.
     *
     */
    private final class CloudPublisherServiceTrackerCustomizer
            implements ServiceTrackerCustomizer<CloudPublisher, CloudPublisher> {

        @Override
        public CloudPublisher addingService(final ServiceReference<CloudPublisher> reference) {
            ExamplePublisher.this.cloudPublisher = ExamplePublisher.this.bundleContext.getService(reference);
            ExamplePublisher.this.cloudPublisher.register(ExamplePublisher.this);

            return ExamplePublisher.this.cloudPublisher;
        }

        @Override
        public void modifiedService(final ServiceReference<CloudPublisher> reference, final CloudPublisher service) {
            // Not needed
        }

        @Override
        public void removedService(final ServiceReference<CloudPublisher> reference, final CloudPublisher service) {
            ExamplePublisher.this.cloudPublisher.unregister(ExamplePublisher.this);
            ExamplePublisher.this.cloudPublisher = null;
        }
    }

    private final class CloudSubscriberServiceTrackerCustomizer
            implements ServiceTrackerCustomizer<CloudSubscriber, CloudSubscriber> {

        @Override
        public CloudSubscriber addingService(final ServiceReference<CloudSubscriber> reference) {
            ExamplePublisher.this.cloudSubscriber = ExamplePublisher.this.bundleContext.getService(reference);
            ExamplePublisher.this.cloudSubscriber.register((CloudSubscriberListener) ExamplePublisher.this);
            ExamplePublisher.this.cloudSubscriber.register((CloudConnectionListener) ExamplePublisher.this);

            return ExamplePublisher.this.cloudSubscriber;
        }

        @Override
        public void modifiedService(final ServiceReference<CloudSubscriber> reference, final CloudSubscriber service) {
            // Not needed
        }

        @Override
        public void removedService(final ServiceReference<CloudSubscriber> reference, final CloudSubscriber service) {
            ExamplePublisher.this.cloudSubscriber.unregister((CloudSubscriberListener) ExamplePublisher.this);
            ExamplePublisher.this.cloudSubscriber.unregister((CloudConnectionListener) ExamplePublisher.this);
            ExamplePublisher.this.cloudSubscriber = null;
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(ExamplePublisher.class);

    private ServiceTrackerCustomizer<CloudPublisher, CloudPublisher> cloudServiceTrackerCustomizer;
    private ServiceTracker<CloudPublisher, CloudPublisher> cloudPublisherTracker;
    private CloudPublisher cloudPublisher;

    private ServiceTrackerCustomizer<CloudSubscriber, CloudSubscriber> cloudSubscriberServiceTrackerCustomizer;
    private ServiceTracker<CloudSubscriber, CloudSubscriber> cloudSubscriberTracker;
    private CloudSubscriber cloudSubscriber;

    private ScheduledExecutorService worker;
    private ScheduledFuture<?> handle;

    private float temperature;
    private Map<String, Object> properties;

    private BundleContext bundleContext;

    private ExamplePublisherOptions examplePublisherOptions;

    // ----------------------------------------------------------------
    //
    // Activation APIs
    //
    // ----------------------------------------------------------------

    protected void activate(ComponentContext componentContext, Map<String, Object> properties) {
        logger.info("Activating ExamplePublisher...");

        // start worker
        this.worker = Executors.newSingleThreadScheduledExecutor();

        this.properties = properties;
        dumpProperties("Activate", properties);

        this.bundleContext = componentContext.getBundleContext();

        this.examplePublisherOptions = new ExamplePublisherOptions(properties);

        this.cloudServiceTrackerCustomizer = new CloudPublisherServiceTrackerCustomizer();
        initCloudPublisherTracking();

        this.cloudSubscriberServiceTrackerCustomizer = new CloudSubscriberServiceTrackerCustomizer();
        initCloudSubscriberTracking();

        doUpdate();

        logger.info("Activating ExamplePublisher... Done.");
    }

    protected void deactivate(ComponentContext componentContext) {
        logger.info("Deactivating ExamplePublisher...");

        // shutting down the worker and cleaning up the properties
        this.worker.shutdown();
        // close the client

        if (nonNull(this.cloudPublisherTracker)) {
            this.cloudPublisherTracker.close();
        }

        if (nonNull(this.cloudSubscriberTracker)) {
            this.cloudSubscriberTracker.close();
        }

        logger.info("Deactivating ExamplePublisher... Done.");
    }

    public void updated(Map<String, Object> properties) {
        logger.info("Updated ExamplePublisher...");

        // store the properties received
        this.properties = properties;
        dumpProperties("Update", properties);

        this.examplePublisherOptions = new ExamplePublisherOptions(properties);

        if (nonNull(this.cloudPublisherTracker)) {
            this.cloudPublisherTracker.close();
        }
        initCloudPublisherTracking();

        if (nonNull(this.cloudSubscriberTracker)) {
            this.cloudSubscriberTracker.close();
        }
        initCloudSubscriberTracking();

        // try to kick off a new job
        doUpdate();
        logger.info("Updated ExamplePublisher... Done.");
    }

    // ----------------------------------------------------------------
    //
    // Private Methods
    //
    // ----------------------------------------------------------------

    /**
     * Dump properties in stable order
     *
     * @param properties
     *            the properties to dump
     */
    private static void dumpProperties(final String action, final Map<String, Object> properties) {
        final Set<String> keys = new TreeSet<>(properties.keySet());
        for (final String key : keys) {
            logger.info("{} - {}: {}", action, key, properties.get(key));
        }
    }

    /**
     * Called after a new set of properties has been configured on the service
     */
    private void doUpdate() {
        // cancel a current worker handle if one if active
        if (this.handle != null) {
            this.handle.cancel(true);
        }

        // reset the temperature to the initial value
        this.temperature = this.examplePublisherOptions.getTempInitial();

        // schedule a new worker based on the properties of the service
        int pubrate = this.examplePublisherOptions.getPublishRate();
        this.handle = this.worker.scheduleAtFixedRate(new Runnable() {

            @Override
            public void run() {
                doPublish();
            }
        }, 0, pubrate, TimeUnit.MILLISECONDS);
    }

    /**
     * Called at the configured rate to publish the next temperature measurement.
     */
    private void doPublish() {
        // Increment the simulated temperature value
        float tempIncr = this.examplePublisherOptions.getTempIncrement();
        this.temperature += tempIncr;

        // Allocate a new payload
        KuraPayload payload = new KuraPayload();

        // Timestamp the message
        payload.setTimestamp(new Date());

        // Add the temperature as a metric to the payload
        payload.addMetric("temperature", this.temperature);

        // add all the other metrics
        for (String metric : this.examplePublisherOptions.getMetricsPropertiesNames()) {
            if ("metric.char".equals(metric)) {
                // publish character as a string as the
                // "char" type is not supported in the Kura Payload
                payload.addMetric(metric, String.valueOf(this.properties.get(metric)));
            } else if ("metric.short".equals(metric)) {
                // publish short as an integer as the
                // "short " type is not supported in the Kura Payload
                payload.addMetric(metric, ((Short) this.properties.get(metric)).intValue());
            } else if ("metric.byte".equals(metric)) {
                // publish byte as an integer as the
                // "byte" type is not supported in the Kura Payload
                payload.addMetric(metric, ((Byte) this.properties.get(metric)).intValue());
            } else {
                payload.addMetric(metric, this.properties.get(metric));
            }
        }

        // Publish the message
        try {
            if (nonNull(this.cloudPublisher)) {
                int messageId = this.cloudPublisher.publish(payload);
                logger.info("Published to message: {} with ID: {}", new Object[] { payload, messageId });
            }
        } catch (Exception e) {
            logger.error("Cannot publish: ", e);
        }
    }

    private void initCloudPublisherTracking() {
        String selectedCloudPublisherPid = this.examplePublisherOptions.getCloudPublisherPid();
        String filterString = String.format("(&(%s=%s)(kura.service.pid=%s))", Constants.OBJECTCLASS,
                CloudPublisher.class.getName(), selectedCloudPublisherPid);
        Filter filter = null;
        try {
            filter = this.bundleContext.createFilter(filterString);
        } catch (InvalidSyntaxException e) {
            logger.error("Filter setup exception ", e);
        }
        this.cloudPublisherTracker = new ServiceTracker<>(this.bundleContext, filter,
                this.cloudServiceTrackerCustomizer);
        this.cloudPublisherTracker.open();
    }

    private void initCloudSubscriberTracking() {
        String selectedCloudSubscriberPid = this.examplePublisherOptions.getCloudSubscriberPid();
        String filterString = String.format("(&(%s=%s)(kura.service.pid=%s))", Constants.OBJECTCLASS,
                CloudSubscriber.class.getName(), selectedCloudSubscriberPid);
        Filter filter = null;
        try {
            filter = this.bundleContext.createFilter(filterString);
        } catch (InvalidSyntaxException e) {
            logger.error("Filter setup exception ", e);
        }
        this.cloudSubscriberTracker = new ServiceTracker<>(this.bundleContext, filter,
                this.cloudSubscriberServiceTrackerCustomizer);
        this.cloudSubscriberTracker.open();
    }

    private void logReceivedMessage(KuraPayload msg) {
        Date timestamp = msg.getTimestamp();
        if (timestamp != null) {
            logger.info("Message timestamp: {}", timestamp.getTime());
        }

        KuraPosition position = msg.getPosition();
        if (position != null) {
            logger.info("Position latitude: {}", position.getLatitude());
            logger.info("         longitude: {}", position.getLongitude());
            logger.info("         altitude: {}", position.getAltitude());
            logger.info("         heading: {}", position.getHeading());
            logger.info("         precision: {}", position.getPrecision());
            logger.info("         satellites: {}", position.getSatellites());
            logger.info("         speed: {}", position.getSpeed());
            logger.info("         status: {}", position.getStatus());
            logger.info("         timestamp: {}", position.getTimestamp());
        }

        byte[] body = msg.getBody();
        if (body != null && body.length != 0) {
            logger.info("Body lenght: {}", body.length);
        }

        if (msg.metrics() != null) {
            for (Entry<String, Object> entry : msg.metrics().entrySet()) {
                logger.info("Message metric: {}, value: {}", entry.getKey(), entry.getValue());
            }
        }
    }

    @Override
    public void onConnectionEstablished() {
        logger.info("Connection established");
    }

    @Override
    public void onConnectionLost() {
        logger.warn("Connection lost!");
    }

    @Override
    public void onMessageArrived(Map<String, Object> properties, KuraPayload payload) {
        logReceivedMessage(payload);
    }

    @Override
    public void onDisconnected() {
        logger.warn("On disconnected");
    }
}
