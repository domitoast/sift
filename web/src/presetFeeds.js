/*
 * Suggested feeds, offered so a new user does not have to go hunting for a
 * feed URL before the app does anything.
 *
 * Configuration rather than data: nothing a user does can change it, so it
 * lives in code rather than in a table. Every URL here was fetched and
 * verified; a broken entry in a curated list reads as a broken app.
 */

export const PRESET_FEEDS = [
  {
    group: 'Java / Spring',
    items: [
      {
        name: 'Spring Blog',
        url: 'https://spring.io/blog.atom',
        note: 'Spring 官方，版本發布與功能說明',
      },
      {
        name: 'Baeldung',
        url: 'https://www.baeldung.com/feed/',
        note: 'Java 與 Spring 的教學文，面試前很好用',
      },
      {
        name: 'InfoQ',
        url: 'https://feed.infoq.com/',
        note: '架構與工程實務，中英文都有',
      },
    ],
  },
  {
    group: '工程實務',
    items: [
      {
        name: 'Martin Fowler',
        url: 'https://martinfowler.com/feed.atom',
        note: '重構、架構、測試策略的經典來源',
      },
      {
        name: 'Netflix Tech Blog',
        url: 'https://netflixtechblog.com/feed',
        note: '大規模分散式系統的真實案例',
      },
      {
        name: 'Stack Overflow Blog',
        url: 'https://stackoverflow.blog/feed/',
        note: '開發者生態與趨勢',
      },
      {
        name: 'GitHub Blog',
        url: 'https://github.blog/feed/',
        note: '平台功能、開源與安全事件',
      },
    ],
  },
  {
    group: '台灣',
    items: [
      {
        name: 'iThome',
        url: 'https://www.ithome.com.tw/rss',
        note: '台灣最主要的 IT 新聞來源',
      },
      {
        name: 'iT 邦幫忙',
        url: 'https://ithelp.ithome.com.tw/rss/articles',
        note: '技術文章與鐵人賽，中文為主',
      },
      {
        name: 'INSIDE 硬塞的網路趨勢觀察',
        url: 'https://www.inside.com.tw/',
        note: '科技產業與趨勢報導。填首頁，由 autodiscovery 找 feed',
      },
      {
        name: 'Huli 的部落格',
        url: 'https://blog.huli.tw/atom.xml',
        note: '前端與資安，文章寫得很深',
      },
    ],
  },
  {
    group: '語言與社群',
    items: [
      {
        name: 'Rust Blog',
        url: 'https://blog.rust-lang.org/feed.xml',
        note: 'Rust 官方發布',
      },
      {
        name: 'Hacker News',
        url: 'https://news.ycombinator.com/rss',
        note: '量大，但只有標題與留言連結（沒有內文可摘要）',
      },
      {
        name: 'Simon Willison',
        url: 'https://simonwillison.net/atom/everything/',
        note: '大量 LLM 與工具相關的實作筆記',
      },
    ],
  },
]
