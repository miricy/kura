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
package org.eclipse.kura.web.client.ui.CloudServices;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.kura.web.client.messages.Messages;
import org.eclipse.kura.web.client.ui.AlertDialog;
import org.eclipse.kura.web.client.util.FailureHandler;
import org.eclipse.kura.web.client.util.PidTextBox;
import org.eclipse.kura.web.client.util.request.RequestContext;
import org.eclipse.kura.web.client.util.request.RequestQueue;
import org.eclipse.kura.web.shared.model.GwtCloudComponentFactories;
import org.eclipse.kura.web.shared.model.GwtCloudConnectionEntry;
import org.eclipse.kura.web.shared.model.GwtCloudEntry;
import org.eclipse.kura.web.shared.model.GwtCloudPubSubEntry;
import org.eclipse.kura.web.shared.service.GwtCloudService;
import org.eclipse.kura.web.shared.service.GwtCloudServiceAsync;
import org.eclipse.kura.web.shared.service.GwtSecurityTokenService;
import org.eclipse.kura.web.shared.service.GwtSecurityTokenServiceAsync;
import org.eclipse.kura.web.shared.service.GwtStatusService;
import org.eclipse.kura.web.shared.service.GwtStatusServiceAsync;
import org.gwtbootstrap3.client.ui.Button;
import org.gwtbootstrap3.client.ui.Icon;
import org.gwtbootstrap3.client.ui.ListBox;
import org.gwtbootstrap3.client.ui.Modal;
import org.gwtbootstrap3.client.ui.ModalBody;
import org.gwtbootstrap3.client.ui.ModalFooter;
import org.gwtbootstrap3.client.ui.ModalHeader;
import org.gwtbootstrap3.client.ui.Well;
import org.gwtbootstrap3.client.ui.form.validator.RegExValidator;
import org.gwtbootstrap3.client.ui.gwt.CellTable;
import org.gwtbootstrap3.client.ui.html.Span;

import com.google.gwt.cell.client.Cell.Context;
import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.TextColumn;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.ListDataProvider;
import com.google.gwt.view.client.SingleSelectionModel;

public class CloudInstancesUi extends Composite {

    private static CloudConnectionsUiUiBinder uiBinder = GWT.create(CloudConnectionsUiUiBinder.class);
    private static final Messages MSGS = GWT.create(Messages.class);

    private final SingleSelectionModel<GwtCloudEntry> selectionModel = new SingleSelectionModel<>();
    private final ListDataProvider<GwtCloudEntry> cloudServicesDataProvider = new ListDataProvider<>();
    private final GwtSecurityTokenServiceAsync gwtXSRFService = GWT.create(GwtSecurityTokenService.class);
    private final GwtCloudServiceAsync gwtCloudService = GWT.create(GwtCloudService.class);
    private final GwtStatusServiceAsync gwtStatusService = GWT.create(GwtStatusService.class);

    private final CloudServicesUi cloudServicesUi;

    interface CloudConnectionsUiUiBinder extends UiBinder<Widget, CloudInstancesUi> {
    }

    private static final Comparator<GwtCloudEntry> CLOUD_ENTRY_COMPARTOR = (o1, o2) -> o1.getPid()
            .compareTo(o2.getPid());

    @UiField
    Well connectionsWell;
    @UiField
    Button connectionRefresh;
    @UiField
    Button newConnection;
    @UiField
    Button newPubSub;
    @UiField
    Button deleteConnection;
    @UiField
    Button statusConnect;
    @UiField
    Button statusDisconnect;
    @UiField
    Button btnCreateComp;
    @UiField
    Button btnCancel;
    @UiField
    Modal newConnectionModal;
    @UiField
    ListBox cloudFactoriesPids;
    @UiField
    PidTextBox cloudServicePid;
    @UiField
    Icon cloudServicePidSpinner;
    @UiField
    Modal newPubSubModal;
    @UiField
    ListBox pubSubFactoriesPids;
    @UiField
    PidTextBox pubSubPid;
    @UiField
    Button btnPubSubCreateComp;
    @UiField
    Button btnPubSubCancel;
    @UiField
    AlertDialog alertDialog;

    List<String> cloudConnectionFactoryPids;
    Map<String, List<GwtCloudEntry>> pubSubFactoryEntries;

    @UiField
    CellTable<GwtCloudEntry> connectionsGrid = new CellTable<>();

    public CloudInstancesUi(final CloudServicesUi cloudServicesUi) {
        initWidget(uiBinder.createAndBindUi(this));
        this.cloudServicesUi = cloudServicesUi;

        this.connectionsGrid.setSelectionModel(this.selectionModel);

        this.btnCreateComp.addClickHandler(event -> createCloudConnectionServiceFactory());

        this.btnPubSubCreateComp.addClickHandler(event -> createPubSub());

        this.selectionModel.addSelectionChangeHandler(event -> {
            final boolean isConnection = getSelectedObject() instanceof GwtCloudConnectionEntry;

            this.newPubSub.setEnabled(isConnection);
            this.statusConnect.setEnabled(isConnection);
            this.statusDisconnect.setEnabled(isConnection);
            this.cloudServicesUi.onSelectionChange();
        });

        this.cloudFactoriesPids.addChangeHandler(event -> {
            final String factoryPid = this.cloudFactoriesPids.getSelectedValue();
            getSuggestedCloudServicePid(factoryPid);
        });

        initConnectionButtons();

        initConnectionsTable();
    }

    public void setData(final List<GwtCloudEntry> data) {

        final List<GwtCloudEntry> cloudConnections = new ArrayList<>();
        final Map<String, List<GwtCloudEntry>> groupedPubSub = new HashMap<>();

        for (final GwtCloudEntry entry : data) {
            if (entry instanceof GwtCloudConnectionEntry) {
                cloudConnections.add(entry);
                groupedPubSub.put(entry.getPid(), new ArrayList<>());
                continue;
            }

            final GwtCloudPubSubEntry pubSub = (GwtCloudPubSubEntry) entry;

            List<GwtCloudEntry> entries = groupedPubSub.get(pubSub.getCloudConnectionPid());

            if (entries != null) {
                entries.add(pubSub);
            }
        }

        cloudConnections.sort(CLOUD_ENTRY_COMPARTOR);

        final List<GwtCloudEntry> providerList = cloudServicesDataProvider.getList();

        providerList.clear();
        for (final GwtCloudEntry entry : cloudConnections) {
            providerList.add(entry);

            final List<GwtCloudEntry> pubSubs = groupedPubSub.get(entry.getPid());

            if (pubSubs != null) {
                pubSubs.sort(CLOUD_ENTRY_COMPARTOR);
                providerList.addAll(pubSubs);
            }
        }

        refresh();
    }

    public void setFactoryInfo(final GwtCloudComponentFactories factories) {
        this.cloudConnectionFactoryPids = factories.getCloudConnectionFactoryPids();

        this.pubSubFactoryEntries = new HashMap<>();

        for (final GwtCloudEntry pubSubEntry : factories.getPubSubFactories()) {
            final String factoryPid = pubSubEntry.getFactoryPid();

            this.pubSubFactoryEntries.computeIfAbsent(factoryPid, p -> new ArrayList<>()).add(pubSubEntry);
        }
    }

    public boolean setStatus(final String pid, final GwtCloudConnectionEntry.GwtCloudConnectionState state) {
        final List<GwtCloudEntry> entries = cloudServicesDataProvider.getList();

        for (final GwtCloudEntry entry : entries) {
            if (pid.equals(entry.getPid()) && entry instanceof GwtCloudConnectionEntry) {

                final GwtCloudConnectionEntry connEntry = (GwtCloudConnectionEntry) entry;

                connEntry.setState(state);
                connectionsGrid.redraw();
                return true;
            }
        }

        return false;
    }

    public int getTableSize() {
        return this.cloudServicesDataProvider.getList().size();
    }

    public void setVisibility(boolean isVisible) {
        this.connectionsGrid.setVisible(isVisible);
    }

    public GwtCloudEntry getSelectedObject() {
        return this.selectionModel.getSelectedObject();
    }

    public GwtCloudEntry getObjectAfterSelection() {
        final List<GwtCloudEntry> entries = this.cloudServicesDataProvider.getList();

        final int index = entries.indexOf(getSelectedObject());

        if (index == -1) {
            return null;
        }

        final int nextIndex = index + 1;

        if (nextIndex >= entries.size()) {
            return null;
        }

        return entries.get(nextIndex);
    }

    public void setSelected(GwtCloudEntry cloudEntry) {
        this.selectionModel.setSelected(cloudEntry, true);
    }

    private void initConnectionButtons() {
        this.connectionRefresh.addClickHandler(event -> cloudServicesUi.refresh());

        this.newConnection.addClickHandler(event -> showNewConnectionModal());

        this.newPubSub.addClickHandler(event -> {
            final GwtCloudEntry entry = getSelectedObject();

            if (!(entry instanceof GwtCloudConnectionEntry)) {
                return;
            }

            showNewPubSubModal(((GwtCloudConnectionEntry) entry).getCloudConnectionFactoryPid());
        });

        this.deleteConnection.addClickHandler(event -> {

            if (getSelectedObject() instanceof GwtCloudConnectionEntry
                    && getObjectAfterSelection() instanceof GwtCloudPubSubEntry) {
                alertDialog.show(MSGS.cannotDeleteConnection(), AlertDialog.Severity.ALERT, null);
                return;
            }

            if (getTableSize() > 0) {
                showDeleteModal();
            }
        });

        this.statusConnect.addClickHandler(event -> {
            GwtCloudEntry selection = selectionModel.getSelectedObject();
            final String selectedCloudServicePid = selection.getPid();
            connectDataService(selectedCloudServicePid);
        });

        this.statusDisconnect.addClickHandler(event -> {
            GwtCloudEntry selection = selectionModel.getSelectedObject();
            final String selectedCloudServicePid = selection.getPid();
            disconnectDataService(selectedCloudServicePid);
        });
    }

    private void initConnectionsTable() {

        {

            TextColumn<GwtCloudEntry> col = new TextColumn<GwtCloudEntry>() {

                @Override
                public String getValue(GwtCloudEntry object) {
                    final String pid = object.getPid();

                    if (object instanceof GwtCloudPubSubEntry) {
                        return " -> " + pid;
                    } else {
                        return pid;
                    }
                }

                @Override
                public void render(Context context, GwtCloudEntry object, SafeHtmlBuilder sb) {
                    final String pid = object.getPid();

                    if (object instanceof GwtCloudPubSubEntry) {

                        final String iconStyle = ((GwtCloudPubSubEntry) object)
                                .getType() == GwtCloudPubSubEntry.Type.PUBLISHER ? "fa-arrow-up" : "fa-arrow-down";

                        sb.append(() -> "&ensp;<i class=\"fa assets-status-icon " + iconStyle + "\"></i>" + pid);
                    } else {
                        sb.append(() -> "<i class=\"fa assets-status-icon fa-cloud\"></i>" + pid);
                    }
                }
            };
            col.setCellStyleNames("status-table-row");
            this.connectionsGrid.addColumn(col, MSGS.connectionCloudConnectionPidHeader());
        }

        {

            TextColumn<GwtCloudEntry> col = new TextColumn<GwtCloudEntry>() {

                @Override
                public String getValue(GwtCloudEntry object) {

                    if (object instanceof GwtCloudConnectionEntry) {
                        return "Cloud connection";
                    }

                    return ((GwtCloudPubSubEntry) object).getType() == GwtCloudPubSubEntry.Type.PUBLISHER ? "Publisher"
                            : "Subscriber";
                }

            };
            col.setCellStyleNames("status-table-row");
            this.connectionsGrid.addColumn(col, MSGS.typeLabel());
        }

        {
            TextColumn<GwtCloudEntry> col = new TextColumn<GwtCloudEntry>() {

                @Override
                public String getValue(GwtCloudEntry object) {

                    if (!(object instanceof GwtCloudConnectionEntry)) {
                        return "";
                    }

                    final GwtCloudConnectionEntry entry = (GwtCloudConnectionEntry) object;

                    switch (entry.getState()) {
                    case UNREGISTERED:
                        return MSGS.unregistered();
                    case CONNECTED:
                        return MSGS.connected();
                    case DISCONNECTED:
                        return MSGS.disconnected();
                    default:
                        return entry.getState().toString();
                    }
                }

                @Override
                public String getCellStyleNames(Context context, GwtCloudEntry object) {
                    final String defaultStyle = "status-table-row";

                    if (!(object instanceof GwtCloudConnectionEntry)) {
                        return defaultStyle;
                    }

                    final GwtCloudConnectionEntry entry = (GwtCloudConnectionEntry) object;

                    switch (entry.getState()) {
                    case CONNECTED:
                        return defaultStyle + " text-success";
                    case DISCONNECTED:
                        return defaultStyle + " text-danger";
                    default:
                        return defaultStyle;
                    }
                }
            };

            this.connectionsGrid.addColumn(col, MSGS.netIPv4Status());
        }

        {
            TextColumn<GwtCloudEntry> col = new TextColumn<GwtCloudEntry>() {

                @Override
                public String getValue(GwtCloudEntry object) {

                    if (object instanceof GwtCloudConnectionEntry) {
                        return ((GwtCloudConnectionEntry) object).getCloudConnectionFactoryPid();
                    }

                    return object.getFactoryPid();
                }
            };
            col.setCellStyleNames("status-table-row");
            this.connectionsGrid.addColumn(col, MSGS.connectionCloudConnectionFactoryPidHeader());
        }

        this.cloudServicesDataProvider.addDataDisplay(this.connectionsGrid);
    }

    private void createPubSub() {
        final String kuraServicePid = pubSubPid.getPid();

        if (kuraServicePid == null) {
            return;
        }

        newPubSubModal.hide();

        final GwtCloudEntry entry = getSelectedObject();

        if (!(entry instanceof GwtCloudConnectionEntry)) {
            return;
        }

        final String cloudConnectionPid = entry.getPid();
        final String factoryPid = pubSubFactoriesPids.getSelectedValue();

        RequestQueue.submit(context -> gwtXSRFService.generateSecurityToken(
                context.callback(token -> gwtCloudService.createPubSubInstance(token, kuraServicePid, factoryPid,
                        cloudConnectionPid, context.callback(v -> cloudServicesUi.refresh())))));
    }

    private void deletePubSub(final String pid) {

        RequestQueue.submit(context -> gwtXSRFService.generateSecurityToken(context.callback(token -> gwtCloudService
                .deletePubSubInstance(token, pid, context.callback(v -> cloudServicesUi.refresh())))));
    }

    private void createCloudConnectionServiceFactory() {
        final String factoryPid = this.cloudFactoriesPids.getSelectedValue();
        final String newCloudServicePid = this.cloudServicePid.getPid();

        if (newCloudServicePid == null) {
            return;
        }

        RequestQueue.submit(context -> gwtXSRFService
                .generateSecurityToken(context.callback(token -> gwtCloudService.createCloudServiceFromFactory(token,
                        factoryPid, newCloudServicePid, context.callback(new AsyncCallback<Void>() {

                            @Override
                            public void onFailure(Throwable caught) {
                                FailureHandler.handle(caught,
                                        CloudInstancesUi.this.gwtCloudService.getClass().getSimpleName());
                                CloudInstancesUi.this.newConnectionModal.hide();
                            }

                            @Override
                            public void onSuccess(Void result) {
                                CloudInstancesUi.this.newConnectionModal.hide();
                                CloudInstancesUi.this.cloudServicesUi.refresh();
                            }
                        })))));
    }

    private void refresh() {
        int size = this.cloudServicesDataProvider.getList().size();
        this.connectionsGrid.setVisibleRange(0, size);
        this.cloudServicesDataProvider.flush();

        if (size > 0) {
            GwtCloudEntry firstEntry = this.cloudServicesDataProvider.getList().get(0);
            this.selectionModel.setSelected(firstEntry, true);
        }
        this.connectionsGrid.redraw();
    }

    private void showNewPubSubModal(final String connectionFactoryPid) {
        this.pubSubFactoriesPids.clear();
        this.pubSubPid.clear();

        final List<GwtCloudEntry> entries = this.pubSubFactoryEntries.get(connectionFactoryPid);

        if (entries == null || entries.isEmpty()) {
            alertDialog.show(MSGS.noPubSubFactoriesFound(), AlertDialog.Severity.ALERT, null);
            return;
        }

        for (final GwtCloudEntry entry : entries) {
            pubSubFactoriesPids.addItem(entry.getPid());
        }

        newPubSubModal.show();
    }

    private void showNewConnectionModal() {
        this.cloudServicePid.clear();
        this.cloudFactoriesPids.clear();

        for (final String cloudConnectionFactoryPid : cloudConnectionFactoryPids) {
            cloudFactoriesPids.addItem(cloudConnectionFactoryPid);
        }
        String selectedFactoryPid = CloudInstancesUi.this.cloudFactoriesPids.getSelectedValue();
        getSuggestedCloudServicePid(selectedFactoryPid);
        newConnectionModal.show();
    }

    private void connectDataService(final String connectionId) {

        RequestQueue.submit(context -> gwtXSRFService
                .generateSecurityToken(context.callback(token -> CloudInstancesUi.this.gwtStatusService
                        .connectDataService(token, connectionId, context.<Void> callback()))));
    }

    private void disconnectDataService(final String connectionId) {

        RequestQueue.submit(context -> gwtXSRFService
                .generateSecurityToken(context.callback(token -> CloudInstancesUi.this.gwtStatusService
                        .disconnectDataService(token, connectionId, context.<Void> callback()))));

    }

    private void deleteConnection(final String factoryPid, final String cloudServicePid) {

        RequestQueue.submit(context -> gwtXSRFService.generateSecurityToken(
                context.callback(token -> CloudInstancesUi.this.gwtCloudService.deleteCloudServiceFromFactory(token,
                        factoryPid, cloudServicePid,
                        context.callback(result -> CloudInstancesUi.this.cloudServicesUi.refresh())))));

    }

    private void showDeleteModal() {
        final Modal modal = new Modal();

        ModalHeader header = new ModalHeader();
        header.setTitle(MSGS.warning());
        modal.add(header);

        ModalBody body = new ModalBody();
        body.add(new Span(MSGS.cloudServiceDeleteConfirmation()));
        modal.add(body);

        ModalFooter footer = new ModalFooter();
        Button yes = new Button(MSGS.yesButton(), event -> {
            GwtCloudEntry selection = CloudInstancesUi.this.selectionModel.getSelectedObject();

            if (selection instanceof GwtCloudConnectionEntry) {
                final GwtCloudConnectionEntry entry = (GwtCloudConnectionEntry) selection;

                final String selectedFactoryPid = entry.getCloudConnectionFactoryPid();
                final String selectedCloudServicePid = entry.getPid();
                deleteConnection(selectedFactoryPid, selectedCloudServicePid);
            } else {
                deletePubSub(selection.getPid());
            }
            modal.hide();
        });

        Button no = new Button(MSGS.noButton(), event -> modal.hide());

        footer.add(no);
        footer.add(yes);
        modal.add(footer);
        modal.show();
        no.setFocus(true);
    }

    private void getSuggestedCloudServicePid(final String factoryPid) {
        RequestQueue.submit(context -> {
            cloudServicePid.clear();
            cloudServicePid.setEnabled(false);
            cloudServicePidSpinner.setVisible(true);
            gwtCloudService.findSuggestedCloudServicePid(factoryPid,
                    context.callback(result -> getCloudServicePidRegex(context, factoryPid, result)));
        }, false);
    }

    @SuppressWarnings("unchecked")
    private void getCloudServicePidRegex(final RequestContext context, final String factoryPid, final String example) {
        this.gwtCloudService.findCloudServicePidRegex(factoryPid, context.callback(result -> {
            final PidTextBox pidTextBox = CloudInstancesUi.this.cloudServicePid;
            pidTextBox.reset();

            String placeholder = null;
            String validationMessage = null;

            if (example != null) {
                placeholder = MSGS.exampleGiven(example);
                validationMessage = MSGS.mustBeLike(example);
            }

            if (result != null) {
                pidTextBox.setValidators(new RegExValidator(result, validationMessage));
            } else {
                pidTextBox.setValidators();
            }
            pidTextBox.setPlaceholder(placeholder);
            pidTextBox.setEnabled(true);
            cloudServicePidSpinner.setVisible(false);
        }));
    }
}