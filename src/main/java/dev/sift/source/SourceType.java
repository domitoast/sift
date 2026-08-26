package dev.sift.source;

/**
 * 訂閱來源的格式。
 *
 * <p>兩者都是 XML 格式的內容摘要標準，欄位名稱不同但用途相同。
 * 抓取時需要據此決定用哪個解析器。
 */
public enum SourceType {

    /** 最常見的格式，多數部落格與新聞站使用。 */
    RSS,

    /** 較新的標準，規格比 RSS 嚴謹。 */
    ATOM
}
