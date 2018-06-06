/*******************************************************************************
 * Copyright (c) 2016, 2018 Eurotech and/or its affiliates and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Eurotech
 *     Red Hat Inc
 *******************************************************************************/
package org.eclipse.kura.web.server;

import static java.lang.String.format;
import static org.eclipse.kura.configuration.ConfigurationService.KURA_SERVICE_PID;
import static org.eclipse.kura.web.server.util.ServiceLocator.applyToAllServices;
import static org.eclipse.kura.web.server.util.ServiceLocator.withAllServices;
import static org.eclipse.kura.web.shared.model.GwtCloudConnectionState.CONNECTED;
import static org.eclipse.kura.web.shared.model.GwtCloudConnectionState.DISCONNECTED;
import static org.eclipse.kura.web.shared.model.GwtCloudConnectionState.UNREGISTERED;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.eclipse.kura.KuraException;
import org.eclipse.kura.cloud.CloudService;
import org.eclipse.kura.cloud.connection.CloudConnectionService;
import org.eclipse.kura.cloud.connection.factory.CloudConnectionServiceFactory;
import org.eclipse.kura.cloud.factory.CloudServiceFactory;
import org.eclipse.kura.web.server.util.ServiceLocator;
import org.eclipse.kura.web.server.util.ServiceLocator.ServiceConsumer;
import org.eclipse.kura.web.shared.GwtKuraErrorCode;
import org.eclipse.kura.web.shared.GwtKuraException;
import org.eclipse.kura.web.shared.model.GwtAllTypesReference;
import org.eclipse.kura.web.shared.model.GwtCloudConnectionEntry;
import org.eclipse.kura.web.shared.model.GwtGroupedNVPair;
import org.eclipse.kura.web.shared.model.GwtXSRFToken;
import org.eclipse.kura.web.shared.service.GwtCloudService;
import org.osgi.framework.ServiceReference;

public class GwtCloudServiceImpl extends OsgiRemoteServiceServlet implements GwtCloudService {

    private static final long serialVersionUID = 2595835826149606703L;

    private static final String KURA_UI_CSF_PID_DEFAULT = "kura.ui.csf.pid.default";
    private static final String KURA_UI_CSF_PID_REGEX = "kura.ui.csf.pid.regex";

    private static final Comparator<GwtCloudConnectionEntry> CLOUD_CONNECTION_ENTRY_COMPARTOR = new Comparator<GwtCloudConnectionEntry>() {

        @Override
        public int compare(GwtCloudConnectionEntry o1, GwtCloudConnectionEntry o2) {
            int rc;

            rc = o1.getCloudFactoryPid().compareTo(o2.getCloudFactoryPid());
            if (rc != 0) {
                return rc;
            }

            return o1.getCloudServicePid().compareTo(o2.getCloudServicePid());
        }
    };

    private static final Comparator<GwtGroupedNVPair> GROUPED_PAIR_NAME_COMPARATOR = new Comparator<GwtGroupedNVPair>() {

        @Override
        public int compare(GwtGroupedNVPair o1, GwtGroupedNVPair o2) {
            return o1.getValue().compareTo(o2.getValue());
        }
    };

    @Override
    public GwtAllTypesReference referenceTypesEnums() {
        return null;
    }

    @Override
    public List<GwtCloudConnectionEntry> findCloudServices() throws GwtKuraException {

        final Set<GwtCloudConnectionEntry> pairs = new HashSet<>();

        ServiceLocator.applyToAllServices(o -> {

            final CloudConnectionServiceFactory service = wrap(o);

            final String factoryPid = service.getFactoryPid();
            if (factoryPid == null) {
                return;
            }

            for (final String pid : service.getManagedCloudServicePids()) {
                if (pid == null) {
                    continue;
                }

                final GwtCloudConnectionEntry cloudConnectionEntry = new GwtCloudConnectionEntry();
                cloudConnectionEntry.setCloudFactoryPid(factoryPid);
                cloudConnectionEntry.setCloudServicePid(pid);

                fillState(cloudConnectionEntry);

                pairs.add(cloudConnectionEntry);
            }

        }, CloudConnectionServiceFactory.class, CloudServiceFactory.class);

        return pairs.stream().sorted(CLOUD_CONNECTION_ENTRY_COMPARTOR).collect(Collectors.toList());
    }

    /**
     * Fill the state of a cloud connection entry
     *
     * @param cloudConnectionEntry
     *            the entry to fill
     */
    protected void fillState(final GwtCloudConnectionEntry cloudConnectionEntry) throws GwtKuraException {

        cloudConnectionEntry.setState(UNREGISTERED);

        final String filter = format("(%s=%s)", KURA_SERVICE_PID, cloudConnectionEntry.getCloudServicePid());
        withAllServices(CloudConnectionService.class, filter, new ServiceConsumer<CloudConnectionService>() {

            @Override
            public void consume(final CloudConnectionService service) throws Exception {
                cloudConnectionEntry.setState(service.isConnected() ? CONNECTED : DISCONNECTED);
            }
        });

        withAllServices(CloudService.class, filter, new ServiceConsumer<CloudService>() {

            @Override
            public void consume(final CloudService service) throws Exception {
                cloudConnectionEntry.setState(service.isConnected() ? CONNECTED : DISCONNECTED);
            }
        });
    }

    @Override
    public List<GwtGroupedNVPair> findCloudServiceFactories() throws GwtKuraException {
        Set<GwtGroupedNVPair> pairs = new HashSet<>();

        applyToAllServices(o -> {
            final CloudConnectionServiceFactory service = wrap(o);

            if (service.getFactoryPid() == null) {
                return;
            }
            pairs.add(new GwtGroupedNVPair("cloudFactories", "factoryPid", service.getFactoryPid()));
        }, CloudConnectionServiceFactory.class, CloudServiceFactory.class);

        return pairs.stream().sorted(GROUPED_PAIR_NAME_COMPARATOR).collect(Collectors.toList());
    }

    @Override
    public List<String> findStackPidsByFactory(final String factoryPid, final String cloudServicePid)
            throws GwtKuraException {

        // prepare result

        final Set<String> result = new HashSet<>();

        // iterate over all candidates

        applyToAllServices(o -> {
            final CloudConnectionServiceFactory factory = wrap(o);

            if (factoryPid.equals(factory.getFactoryPid())) {
                result.addAll(factory.getStackComponentsPids(cloudServicePid));
            }
        }, CloudConnectionServiceFactory.class, CloudServiceFactory.class);

        // return result

        return new ArrayList<>(result);
    }

    @Override
    public void createCloudServiceFromFactory(GwtXSRFToken xsrfToken, String factoryPid, String cloudServicePid)
            throws GwtKuraException {
        checkXSRFToken(xsrfToken);
        if (factoryPid == null || factoryPid.trim().isEmpty() || cloudServicePid == null
                || cloudServicePid.trim().isEmpty()) {
            throw new GwtKuraException(GwtKuraErrorCode.ILLEGAL_NULL_ARGUMENT);
        }

        final AtomicReference<CloudConnectionServiceFactory> ref = new AtomicReference<>();

        applyToAllServices(o -> {
            final CloudConnectionServiceFactory service = wrap(o);

            if (!service.getFactoryPid().equals(factoryPid) || ref.get() != null) {
                return;
            }

            ref.set(service);

        }, CloudConnectionServiceFactory.class, CloudServiceFactory.class);

        try {
            ref.get().createConfiguration(cloudServicePid);
        } catch (KuraException e) {
            throw new GwtKuraException("Failed to create cloud stack");
        }

    }

    @Override
    public void deleteCloudServiceFromFactory(GwtXSRFToken xsrfToken, String factoryPid, String cloudServicePid)
            throws GwtKuraException {
        if (factoryPid == null || factoryPid.trim().isEmpty() || cloudServicePid == null
                || cloudServicePid.trim().isEmpty()) {
            throw new GwtKuraException(GwtKuraErrorCode.ILLEGAL_NULL_ARGUMENT);
        }

        final AtomicReference<CloudConnectionServiceFactory> ref = new AtomicReference<>();

        applyToAllServices(o -> {
            final CloudConnectionServiceFactory service = wrap(o);

            if (!service.getFactoryPid().equals(factoryPid) || ref.get() != null) {
                return;
            }

            ref.set(service);

        }, CloudConnectionServiceFactory.class, CloudServiceFactory.class);

        try {
            ref.get().deleteConfiguration(cloudServicePid);
        } catch (KuraException e) {
            throw new GwtKuraException("Failed to create cloud stack");
        }

    }

    @Override
    public String findSuggestedCloudServicePid(String factoryPid) throws GwtKuraException {
        Collection<ServiceReference<CloudConnectionServiceFactory>> cloudConnectionServiceFactoryReferences = ServiceLocator
                .getInstance().getServiceReferences(CloudConnectionServiceFactory.class, null);

        for (ServiceReference<CloudConnectionServiceFactory> cloudServiceFactoryReference : cloudConnectionServiceFactoryReferences) {
            CloudConnectionServiceFactory cloudServiceFactory = ServiceLocator.getInstance()
                    .getService(cloudServiceFactoryReference);
            if (!cloudServiceFactory.getFactoryPid().equals(factoryPid)) {
                continue;
            }
            Object propertyObject = cloudServiceFactoryReference.getProperty(KURA_UI_CSF_PID_DEFAULT);
            ServiceLocator.getInstance().ungetService(cloudServiceFactoryReference);
            if (propertyObject != null) {
                return (String) propertyObject;
            }
        }

        Collection<ServiceReference<CloudServiceFactory>> cloudServiceFactoryReferences = ServiceLocator.getInstance()
                .getServiceReferences(CloudServiceFactory.class, null);

        for (ServiceReference<CloudServiceFactory> cloudServiceFactoryReference : cloudServiceFactoryReferences) {
            CloudServiceFactory cloudServiceFactory = ServiceLocator.getInstance()
                    .getService(cloudServiceFactoryReference);
            if (!cloudServiceFactory.getFactoryPid().equals(factoryPid)) {
                continue;
            }
            Object propertyObject = cloudServiceFactoryReference.getProperty(KURA_UI_CSF_PID_DEFAULT);
            ServiceLocator.getInstance().ungetService(cloudServiceFactoryReference);
            if (propertyObject != null) {
                return (String) propertyObject;
            }
        }
        return null;
    }

    @Override
    public String findCloudServicePidRegex(String factoryPid) throws GwtKuraException {
        Collection<ServiceReference<CloudConnectionServiceFactory>> cloudConnectionServiceFactoryReferences = ServiceLocator
                .getInstance().getServiceReferences(CloudConnectionServiceFactory.class, null);

        for (ServiceReference<CloudConnectionServiceFactory> cloudServiceFactoryReference : cloudConnectionServiceFactoryReferences) {
            CloudConnectionServiceFactory cloudServiceFactory = ServiceLocator.getInstance()
                    .getService(cloudServiceFactoryReference);
            if (!cloudServiceFactory.getFactoryPid().equals(factoryPid)) {
                continue;
            }
            Object propertyObject = cloudServiceFactoryReference.getProperty(KURA_UI_CSF_PID_REGEX);
            ServiceLocator.getInstance().ungetService(cloudServiceFactoryReference);
            if (propertyObject != null) {
                return (String) propertyObject;
            }
        }

        Collection<ServiceReference<CloudServiceFactory>> cloudServiceFactoryReferences = ServiceLocator.getInstance()
                .getServiceReferences(CloudServiceFactory.class, null);

        for (ServiceReference<CloudServiceFactory> cloudServiceFactoryReference : cloudServiceFactoryReferences) {
            CloudServiceFactory cloudServiceFactory = ServiceLocator.getInstance()
                    .getService(cloudServiceFactoryReference);
            if (!cloudServiceFactory.getFactoryPid().equals(factoryPid)) {
                continue;
            }
            Object propertyObject = cloudServiceFactoryReference.getProperty(KURA_UI_CSF_PID_REGEX);
            ServiceLocator.getInstance().ungetService(cloudServiceFactoryReference);
            if (propertyObject != null) {
                return (String) propertyObject;
            }
        }

        return null;
    }

    public CloudConnectionServiceFactory wrap(final Object o) {
        if (o instanceof CloudConnectionServiceFactory) {
            return (CloudConnectionServiceFactory) o;
        } else if (o instanceof CloudServiceFactory) {
            final CloudServiceFactory f = (CloudServiceFactory) o;

            return new CloudConnectionServiceFactory() {

                @Override
                public List<String> getStackComponentsPids(String pid) throws KuraException {
                    return f.getStackComponentsPids(pid);
                }

                @Override
                public Set<String> getManagedCloudServicePids() throws KuraException {
                    return f.getManagedCloudServicePids();
                }

                @Override
                public String getFactoryPid() {
                    return f.getFactoryPid();
                }

                @Override
                public void deleteConfiguration(String pid) throws KuraException {
                    f.deleteConfiguration(pid);
                }

                @Override
                public void createConfiguration(String pid) throws KuraException {
                    f.createConfiguration(pid);
                }
            };
        }
        return null;
    }
}