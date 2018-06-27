/*******************************************************************************
 * Copyright (c) 2018 Eurotech and/or its affiliates and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 *******************************************************************************/
package org.eclipse.kura.cloudconnection.request;

import org.eclipse.kura.KuraException;
import org.osgi.annotation.versioning.ProviderType;

/**
 * Interface used to register or unregister {@link RequestHandler}s identified by a specific id
 * 
 * @noextend This class is not intended to be subclassed by clients.
 * @since 2.0
 */
@ProviderType
public interface RequestHandlerRegistry {

    public void register(String id, RequestHandler cloudlet) throws KuraException;

    public void unregister(String id) throws KuraException;

}
