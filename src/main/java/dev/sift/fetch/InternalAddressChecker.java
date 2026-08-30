package dev.sift.fetch;

import org.springframework.stereotype.Component;

import java.net.InetAddress;

/**
 * 判斷一個 IP 是不是「內部位址」。
 *
 * <p><b>這個類別完全不碰網路。</b> 給它一個 {@link InetAddress}，
 * 它只做數字比對回答是或否。因此它的測試是純 unit test，
 * 而且是整個 SSRF 防護裡最該被測到的那一段。
 *
 * <p>「內部」的定義只有一句話：
 * <b>這個位址從網際網路上連不到，所以使用者不可能有正當理由訂閱它。</b>
 */
@Component
public class InternalAddressChecker {

    /**
     * @return true 代表這是內部位址，不應該連過去
     */
    public boolean isInternal(InetAddress address) {

        return address.isLoopbackAddress()       // 127.0.0.0/8、::1 —— 這台機器自己
                || address.isLinkLocalAddress()   // 169.254.0.0/16 —— 雲端 metadata
                || address.isSiteLocalAddress()   // 10.x、172.16-31.x、192.168.x —— 內網
                || address.isAnyLocalAddress()    // 0.0.0.0、:: —— 「所有位址」
                || address.isMulticastAddress();  // 224.0.0.0/4 —— 群播
    }
}
