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
package org.eclipse.kura.cloudconnection.request;

import static java.util.Objects.requireNonNull;

import java.util.List;

import org.osgi.annotation.versioning.ProviderType;

/**
 * Resources associated to the received request.
 * 
 * @noextend This class is not intended to be subclassed by clients.
 * @since 2.0
 */
@ProviderType
public class RequestHandlerResources {

    private final List<String> resources;

    public RequestHandlerResources(List<String> resources) {
        this.resources = requireNonNull(resources);
    }

    public List<String> getResources() {
        return this.resources;
    }

}
