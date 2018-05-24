package org.eclipse.kura.cloud.publisher;

import org.eclipse.kura.KuraException;
import org.eclipse.kura.message.KuraPayload;
import org.osgi.annotation.versioning.ProviderType;

/**
 * @since 1.5
 */
@ProviderType
public interface CloudPublisher {
    
    public int publish(KuraPayload message) throws KuraException;

}
