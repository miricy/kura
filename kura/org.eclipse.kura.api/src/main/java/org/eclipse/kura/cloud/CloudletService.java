package org.eclipse.kura.cloud;

import org.eclipse.kura.KuraException;

/**
 * @since 1.5
 */
public interface CloudletService {
    
    public void registerCloudlet(String appId, CloudletInterface cloudlet) throws KuraException;
    
    public void unregisterCloudlet(String appId) throws KuraException;

}
