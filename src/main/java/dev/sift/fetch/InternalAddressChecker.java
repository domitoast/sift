package dev.sift.fetch;

import org.springframework.stereotype.Component;

import java.net.InetAddress;

/**
 * Blocks loopback, private and cloud metadata addresses.
 *
 * The check runs on the resolved IP rather than the hostname, because a public
 * hostname can resolve to a private address.
 */
@Component
public class InternalAddressChecker {
    public boolean isInternal(InetAddress address) {
        return address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isAnyLocalAddress()
                || address.isMulticastAddress();
    }
}
