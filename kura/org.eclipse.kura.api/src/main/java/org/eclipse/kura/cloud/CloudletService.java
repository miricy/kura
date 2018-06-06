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
 * @since 2.0
 */
public interface CloudletService {

    public void register(String id, CloudletInterface cloudlet) throws KuraException;

    public void unregister(String id) throws KuraException;

}
