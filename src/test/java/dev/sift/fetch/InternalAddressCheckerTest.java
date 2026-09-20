package dev.sift.fetch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.assertThat;

class InternalAddressCheckerTest {
    private final InternalAddressChecker checker = new InternalAddressChecker();

    private InetAddress ip(String address) throws UnknownHostException {
        return InetAddress.getByName(address);
    }

    @Test
    @DisplayName("★★ 雲端 metadata（169.254.169.254）→ 內部位址")
    void cloudMetadata_shouldBeInternal() throws Exception {
        assertThat(checker.isInternal(ip("169.254.169.254"))).isTrue();
    }

    @Test
    @DisplayName("★ loopback：127.0.0.1 與 localhost → 內部位址")
    void loopback_shouldBeInternal() throws Exception {
        assertThat(checker.isInternal(ip("127.0.0.1"))).isTrue();
        assertThat(checker.isInternal(ip("localhost"))).isTrue();

        assertThat(checker.isInternal(ip("127.1.2.3"))).isTrue();
    }

    @Test
    @DisplayName("★ 三段私有網段都要擋")
    void privateRanges_shouldBeInternal() throws Exception {
        assertThat(checker.isInternal(ip("10.0.0.5"))).isTrue();
        assertThat(checker.isInternal(ip("172.16.0.1"))).isTrue();
        assertThat(checker.isInternal(ip("172.31.255.254"))).isTrue();
        assertThat(checker.isInternal(ip("192.168.1.1"))).isTrue();
    }

    @Test
    @DisplayName("0.0.0.0 → 內部位址")
    void anyLocal_shouldBeInternal() throws Exception {
        assertThat(checker.isInternal(ip("0.0.0.0"))).isTrue();
    }

    @Test
    @DisplayName("★ IPv6 的內部位址也要擋")
    void ipv6Internal_shouldBeInternal() throws Exception {
        assertThat(checker.isInternal(ip("::1"))).isTrue();
        assertThat(checker.isInternal(ip("fe80::1"))).isTrue();
    }

    @Test
    @DisplayName("★ 公開 IP 要放行——擋太多等於功能壞掉")
    void publicAddresses_shouldNotBeInternal() throws Exception {
        assertThat(checker.isInternal(ip("8.8.8.8"))).isFalse();
        assertThat(checker.isInternal(ip("1.1.1.1"))).isFalse();
        assertThat(checker.isInternal(ip("209.216.230.240"))).isFalse();
    }

    @Test
    @DisplayName("★ 172.15 與 172.32 是公開的——私有範圍只有 172.16–172.31")
    void adjacentToPrivateRange_shouldNotBeInternal() throws Exception {
        assertThat(checker.isInternal(ip("172.15.255.255"))).isFalse();
        assertThat(checker.isInternal(ip("172.32.0.0"))).isFalse();
    }
}
