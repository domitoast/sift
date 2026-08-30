package dev.sift.fetch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SSRF 防護的核心判斷。
 *
 * <p><b>這是全專案最重要的一組測試。</b>
 * 在它存在之前，只要有人刪掉 {@code isInternal} 裡的任何一行，
 * 其餘 77 個測試照樣全綠——那道防線靠的是「沒有人會去改它」。
 *
 * <p>不碰網路：{@code getByName("10.0.0.5")} 對於純數字的 IP
 * 不會發出 DNS 查詢，只是把字串轉成位址物件。
 */
class InternalAddressCheckerTest {

    private final InternalAddressChecker checker = new InternalAddressChecker();

    private InetAddress ip(String address) throws UnknownHostException {
        return InetAddress.getByName(address);
    }

    // ---------- 必須擋下的 ----------

    @Test
    @DisplayName("★★ 雲端 metadata（169.254.169.254）→ 內部位址")
    void cloudMetadata_shouldBeInternal() throws Exception {

        /*
         * 這一條保護的是整個專案最嚴重的風險。
         *
         * 在 AWS / GCP / Azure 上，這個位址會回傳這台機器的憑證，
         * 而且不需要任何認證——它的假設是「連得到我就代表你在這台機器上」。
         *
         * 2019 年 Capital One 一億筆資料外洩，起點就是打到這個位址。
         */
        assertThat(checker.isInternal(ip("169.254.169.254"))).isTrue();
    }

    @Test
    @DisplayName("★ loopback：127.0.0.1 與 localhost → 內部位址")
    void loopback_shouldBeInternal() throws Exception {

        assertThat(checker.isInternal(ip("127.0.0.1"))).isTrue();
        assertThat(checker.isInternal(ip("localhost"))).isTrue();

        // 127.x.x.x 整段都是 loopback，不是只有 127.0.0.1
        assertThat(checker.isInternal(ip("127.1.2.3"))).isTrue();
    }

    @Test
    @DisplayName("★ 三段私有網段都要擋")
    void privateRanges_shouldBeInternal() throws Exception {

        assertThat(checker.isInternal(ip("10.0.0.5"))).isTrue();        // 10.0.0.0/8
        assertThat(checker.isInternal(ip("172.16.0.1"))).isTrue();      // 172.16.0.0/12 起點
        assertThat(checker.isInternal(ip("172.31.255.254"))).isTrue();  // 172.16.0.0/12 終點
        assertThat(checker.isInternal(ip("192.168.1.1"))).isTrue();     // 192.168.0.0/16
    }

    @Test
    @DisplayName("0.0.0.0 → 內部位址")
    void anyLocal_shouldBeInternal() throws Exception {

        assertThat(checker.isInternal(ip("0.0.0.0"))).isTrue();
    }

    @Test
    @DisplayName("★ IPv6 的內部位址也要擋")
    void ipv6Internal_shouldBeInternal() throws Exception {

        /*
         * 只擋 IPv4 是常見的漏洞：
         * 攻擊者把網域指向 ::1，IPv4 的檢查全部不成立，就繞過去了。
         */
        assertThat(checker.isInternal(ip("::1"))).isTrue();              // loopback
        assertThat(checker.isInternal(ip("fe80::1"))).isTrue();          // link-local
    }

    // ---------- 必須放行的 ----------

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

        /*
         * 這一條在測「邊界」。
         *
         * 172.16.0.0/12 是一個很容易記錯的範圍——
         * 常見的錯誤實作是「172 開頭就當私有」，那會把
         * 172.15.x.x 和 172.32.x.x 這些公開位址一起擋掉。
         *
         * 邊界值永遠是最值得測的地方。
         */
        assertThat(checker.isInternal(ip("172.15.255.255"))).isFalse();
        assertThat(checker.isInternal(ip("172.32.0.0"))).isFalse();
    }
}
