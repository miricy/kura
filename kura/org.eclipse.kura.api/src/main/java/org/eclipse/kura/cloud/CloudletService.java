/*******************************************************************************
 * Copyright (c) 2018 Eurotech and/or its affiliates and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 *******************************************************************************/
package org.eclipse.kura.cloud;

import org.eclipse.kura.KuraException;

/**
 * @since 1.5
 */
public interface CloudletService {
    
    public void registerCloudlet(String appId, CloudletInterface cloudlet) throws KuraException;
    
    public void unregisterCloudlet(String appId) throws KuraException;

}
