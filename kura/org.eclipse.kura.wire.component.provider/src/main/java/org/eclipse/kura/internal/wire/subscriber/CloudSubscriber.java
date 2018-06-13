/*******************************************************************************
 * Copyright (c) 2016, 2018 Eurotech and/or its affiliates and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Eurotech
 *  Amit Kumar Mondal
 *
 *******************************************************************************/
package org.eclipse.kura.internal.wire.subscriber;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.Objects.requireNonNull;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.kura.cloud.subscriber.listener.CloudSubscriberListener;
import org.eclipse.kura.configuration.ConfigurableComponent;
import org.eclipse.kura.message.KuraPayload;
import org.eclipse.kura.type.TypedValue;
import org.eclipse.kura.type.TypedValues;
import org.eclipse.kura.wire.WireComponent;
import org.eclipse.kura.wire.WireEmitter;
import org.eclipse.kura.wire.WireEnvelope;
import org.eclipse.kura.wire.WireHelperService;
import org.eclipse.kura.wire.WireRecord;
import org.eclipse.kura.wire.WireSupport;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.wireadmin.Wire;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Class CloudSubscriber is the specific Wire Component to subscribe a list
 * of {@link WireRecord}s as received in {@link WireEnvelope} from the configured cloud
 * platform.<br/>
 * <br/>
 *
 * For every {@link WireRecord} as found in {@link WireEnvelope} will be wrapped inside a Kura
 * Payload and will be sent to the Cloud Platform. Unlike Cloud Publisher Wire
 * Component, the user can only avail to wrap every {@link WireRecord} in the default
 * Google Protobuf Payload.
 */
public final class CloudSubscriber implements WireEmitter, ConfigurableComponent, CloudSubscriberListener {

    /**
     * Inner class defined to track the CloudServices as they get added, modified or removed.
     * Specific methods can refresh the cloudService definition and setup again the Cloud Client.
     *
     */
    private final class CloudSubscriberServiceTrackerCustomizer implements
            ServiceTrackerCustomizer<org.eclipse.kura.cloud.subscriber.CloudSubscriber, org.eclipse.kura.cloud.subscriber.CloudSubscriber> {

        @Override
        public org.eclipse.kura.cloud.subscriber.CloudSubscriber addingService(
                final ServiceReference<org.eclipse.kura.cloud.subscriber.CloudSubscriber> reference) {
            final org.eclipse.kura.cloud.subscriber.CloudSubscriber cloudSubscriber = CloudSubscriber.this.bundleContext
                    .getService(reference);
            cloudSubscriber.register(CloudSubscriber.this);
            return cloudSubscriber;
        }

        @Override
        public void modifiedService(final ServiceReference<org.eclipse.kura.cloud.subscriber.CloudSubscriber> reference,
                final org.eclipse.kura.cloud.subscriber.CloudSubscriber service) {
            // no need
        }

        @Override
        public void removedService(final ServiceReference<org.eclipse.kura.cloud.subscriber.CloudSubscriber> reference,
                final org.eclipse.kura.cloud.subscriber.CloudSubscriber service) {
            service.unregister(CloudSubscriber.this);
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(CloudSubscriber.class);

    private BundleContext bundleContext;

    private ServiceTracker<org.eclipse.kura.cloud.subscriber.CloudSubscriber, org.eclipse.kura.cloud.subscriber.CloudSubscriber> cloudSubscriberTracker;

    private CloudSubscriberOptions cloudSubscriberOptions;

    private volatile WireHelperService wireHelperService;

    private WireSupport wireSupport;

    // ----------------------------------------------------------------
    //
    // Dependencies
    //
    // ----------------------------------------------------------------

    /**
     * Binds the Wire Helper Service.
     *
     * @param wireHelperService
     *            the new Wire Helper Service
     */
    public void bindWireHelperService(final WireHelperService wireHelperService) {
        if (isNull(this.wireHelperService)) {
            this.wireHelperService = wireHelperService;
        }
    }

    /**
     * Unbinds the Wire Helper Service.
     *
     * @param wireHelperService
     *            the new Wire Helper Service
     */
    public void unbindWireHelperService(final WireHelperService wireHelperService) {
        if (this.wireHelperService == wireHelperService) {
            this.wireHelperService = null;
        }
    }

    // ----------------------------------------------------------------
    //
    // Activation APIs
    //
    // ----------------------------------------------------------------

    /**
     * OSGi Service Component callback for activation.
     *
     * @param componentContext
     *            the component context
     * @param properties
     *            the properties
     */
    protected void activate(final ComponentContext componentContext, final Map<String, Object> properties) {
        logger.debug("Activating Cloud Subscriber Wire Component...");
        this.bundleContext = componentContext.getBundleContext();
        this.wireSupport = this.wireHelperService.newWireSupport(this,
                (ServiceReference<WireComponent>) componentContext.getServiceReference());
        this.cloudSubscriberOptions = new CloudSubscriberOptions(properties);

        initCloudServiceTracking();
        logger.debug("Activating Cloud Subscriber Wire Component... Done");
    }

    /**
     * OSGi Service Component callback for updating.
     *
     * @param properties
     *            the updated properties
     */
    public void updated(final Map<String, Object> properties) {
        logger.debug("Updating Cloud Subscriber Wire Component...");

        // Update properties
        this.cloudSubscriberOptions = new CloudSubscriberOptions(properties);

        if (nonNull(this.cloudSubscriberTracker)) {
            this.cloudSubscriberTracker.close();
        }
        initCloudServiceTracking();

        logger.debug("Updating Cloud Subscriber Wire Component... Done");
    }

    /**
     * OSGi Service Component callback for deactivation.
     *
     * @param componentContext
     *            the component context
     */
    protected void deactivate(final ComponentContext componentContext) {
        logger.debug("Deactivating Cloud Subscriber Wire Component...");

        if (nonNull(this.cloudSubscriberTracker)) {
            this.cloudSubscriberTracker.close();
        }
        logger.debug("Deactivating Cloud Subscriber Wire Component... Done");
    }

    /** {@inheritDoc} */
    @Override
    public void consumersConnected(final Wire[] wires) {
        this.wireSupport.consumersConnected(wires);
    }

    /** {@inheritDoc} */
    @Override
    public Object polled(final Wire wires) {
        return this.wireSupport.polled(wires);
    }

    // ----------------------------------------------------------------
    //
    // Private methods
    //
    // ----------------------------------------------------------------

    /**
     * Service tracker to manage Cloud Services
     */
    private void initCloudServiceTracking() {
        String filterString = String.format("(&(%s=%s)(kura.service.pid=%s))", Constants.OBJECTCLASS,
                org.eclipse.kura.cloud.subscriber.CloudSubscriber.class.getName(),
                this.cloudSubscriberOptions.getCloudSubscriberPid());
        Filter filter = null;
        try {
            filter = this.bundleContext.createFilter(filterString);
        } catch (InvalidSyntaxException e) {
            logger.error("Error while building a Bundle Context filter.", e);
        }
        this.cloudSubscriberTracker = new ServiceTracker<>(this.bundleContext, filter,
                new CloudSubscriberServiceTrackerCustomizer());
        this.cloudSubscriberTracker.open();
    }

    /**
     * Builds a list of {@link WireRecord}s from the provided Kura Payload.
     *
     * @param payload
     *            the payload
     * @return a List of {@link WireRecord}s
     * @throws NullPointerException
     *             if the payload provided is null
     */
    private List<WireRecord> buildWireRecord(final KuraPayload payload) {
        requireNonNull(payload, "Payload cannot be null");

        final Map<String, Object> kuraPayloadProperties = payload.metrics();
        final Map<String, TypedValue<?>> wireProperties = new HashMap<>();

        for (Entry<String, Object> entry : kuraPayloadProperties.entrySet()) {
            final String entryKey = entry.getKey();
            final Object entryValue = entry.getValue();

            final TypedValue<?> convertedValue = TypedValues.newTypedValue(entryValue);
            wireProperties.put(entryKey, convertedValue);
        }

        final WireRecord wireRecord = new WireRecord(wireProperties);
        return Arrays.asList(wireRecord);
    }

    @Override
    public void onMessageArrived(Map<String, Object> properties, KuraPayload payload) {
        wireSupport.emit(buildWireRecord(payload));
    }
}
