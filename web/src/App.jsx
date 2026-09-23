/*
 * Main screen: auth, subscriptions, article list, reader and settings.
 *
 * api() is the one function everything goes through. It attaches the access
 * token and, on a 401, refreshes once and replays the request. Exactly once:
 * a loop would turn an expired refresh token into a request storm.
 *
 * Token storage is in auth.js: access token in memory, refresh token in an
 * HttpOnly cookie. On load the page tries a silent refresh to restore the login.
 */

import { useEffect, useState } from 'react'
import Avatar, { AVATAR_COUNT } from './Avatar.jsx'
import Reader from './Reader.jsx'
import Library from './Library.jsx'
import FetchProgress from './FetchProgress.jsx'
import { useFetchJob } from './useFetchJob.js'
import { PRESET_FEEDS } from './presetFeeds.js'
import { getAccessToken, refreshAccessToken, setAccessToken } from './auth.js'

function App() {
  const [menuOpen, setMenuOpen] = useState(false)
  const [avatarId, setAvatarId] = useState(
      () => Number(localStorage.getItem('sift_avatar') ?? 0))

  useEffect(() => {
    localStorage.setItem('sift_avatar', String(avatarId))
  }, [avatarId])

  const [me, setMe] = useState(null)

  const [theme, setTheme] = useState(() => localStorage.getItem('sift_theme') ?? 'dark')

  useEffect(() => {
    document.documentElement.dataset.theme = theme
    localStorage.setItem('sift_theme', theme)
  }, [theme])

  // Only drives what is rendered; the token itself lives in auth.js.
  const [loggedIn, setLoggedIn] = useState(false)

  // True until the silent refresh on load has answered, so the login form
  // does not flash for someone who is still signed in.
  const [restoring, setRestoring] = useState(true)

  useEffect(() => {
    refreshAccessToken()
      .then((renewed) => setLoggedIn(Boolean(renewed)))
      .finally(() => setRestoring(false))
  }, [])
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')

  const [sources, setSources] = useState([])
  const [selectedSourceId, setSelectedSourceId] = useState(null)
  const [items, setItems] = useState([])
  const [detail, setDetail] = useState(null)

  const [readerExpanded, setReaderExpanded] = useState(false)

  const [view, setView] = useState('inbox')

  const [newName, setNewName] = useState('')
  const [newUrl, setNewUrl] = useState('')

  const [addOpen, setAddOpen] = useState(false)

  const [subscribing, setSubscribing] = useState(null)

  const [keyMasked, setKeyMasked] = useState('')
  const [apiKey, setApiKey] = useState('')

  const [providers, setProviders] = useState([])
  const [provider, setProvider] = useState('')

  const [message, setMessage] = useState('')
  const [messageIsError, setMessageIsError] = useState(false)

  const [showDecided, setShowDecided] = useState(false)

  function say(text, isError = false) {
    setMessage(text)
    setMessageIsError(isError)
  }

  // Fetching happens on the server, so the page has to be told to reload itself.
  const fetchJob = useFetchJob(api, () => {
    refreshSources()
    if (selectedSourceId) reloadItems()
  })

  function saveTokens(data) {
    setAccessToken(data.accessToken)
    setLoggedIn(true)
  }

  function clearTokens() {
    setAccessToken('')
    setLoggedIn(false)
  }

  function request(path, options, accessToken) {
    return fetch('/api/v1' + path, {
      ...options,
      headers: {
        'Content-Type': 'application/json',
        Authorization: 'Bearer ' + accessToken,
        ...options.headers,
      },
    })
  }

  async function api(path, options = {}) {
    let res = await request(path, options, getAccessToken())

    if (res.status === 401) {
      const renewed = await refreshAccessToken()

      if (!renewed) {
        clearTokens()
        throw new Error('登入已過期，請重新登入')
      }

      res = await request(path, options, renewed)
    }

    if (!res.ok) {
      const problem = await res.json().catch(() => null)
      throw new Error(problem?.detail ?? `請求失敗（${res.status}）`)
    }

    if (!res.headers.get('content-type')?.includes('application/json')) {
      return null
    }

    return res.json()
  }

  function logout() {
    // The browser attaches the refresh cookie; the server revokes it and clears it.
    fetch('/api/v1/auth/logout', {
      method: 'POST',
      credentials: 'same-origin',
    }).catch(() => {})

    clearTokens()
    setSources([])
    setItems([])
    setSelectedSourceId(null)
    setMe(null)
    setMenuOpen(false)
  }

  async function login() {
    setError('')

    const res = await fetch('/api/v1/auth/login', {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password }),
    })

    if (!res.ok) {
      setError('登入失敗：帳號或密碼不對')
      return
    }

    saveTokens(await res.json())
    setPassword('')

    say('')
  }

  async function register() {
    setError('')

    const res = await fetch('/api/v1/auth/register', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password }),
    })

    if (!res.ok) {
      const problem = await res.json().catch(() => null)
      setError(problem?.detail ?? `註冊失敗（${res.status}）`)
      return
    }

    await login()
  }

  useEffect(() => {
    if (!loggedIn) return

    api('/sources').then(setSources).catch((e) => say(e.message, true))

    api('/llm-providers')
        .then((list) => {
          setProviders(list)

          setProvider((current) => current || list[0] || '')
        })
        .catch(() => {})

    api('/me')
        .then((data) => {
          setMe(data)
          setKeyMasked(data.llmApiKeyMasked ?? '')
          if (data.llmProvider) setProvider(data.llmProvider)
        })
        .catch((e) => say(`讀取個人資料失敗：${e.message}`, true))
  }, [loggedIn])

  useEffect(() => {
    if (!selectedSourceId) return

    setDetail(null)
    api(`/sources/${selectedSourceId}/items?limit=50`)
      .then(setItems)
      .catch((e) => say(e.message, true))
  }, [selectedSourceId])

  const STATUS_ORDER = {
    READY: 0,
    SUMMARIZING: 1,
    NEW: 2,
    FAILED: 3,
    PROMOTED: 4,
    DISCARDED: 5,
  }

  const isDecided = (item) =>
      item.status === 'PROMOTED' || item.status === 'DISCARDED'

  const decidedCount = items.filter(isDecided).length

  const sortedItems = [...items]
      .filter((item) => showDecided || !isDecided(item))
      .sort((a, b) => {
        const byStatus = (STATUS_ORDER[a.status] ?? 9) - (STATUS_ORDER[b.status] ?? 9)
        if (byStatus !== 0) return byStatus

        if (!a.publishedAt) return 1
        if (!b.publishedAt) return -1
        return new Date(b.publishedAt) - new Date(a.publishedAt)
      })

  const hasPending = items.some(
      (i) => i.status === 'NEW' || i.status === 'SUMMARIZING')

  const fetchingSelected = Boolean(
      fetchJob.job && !fetchJob.job.finished && fetchJob.job.sourceId === selectedSourceId)

  useEffect(() => {
    if (!selectedSourceId || !hasPending) return

    const timer = setInterval(() => {
      api(`/sources/${selectedSourceId}/items?limit=50`)
          .then(setItems)
          .catch(() => {})
      refreshSources()
    }, 10000)

    return () => clearInterval(timer)
  }, [selectedSourceId, hasPending])

  function refreshSources() {
    api('/sources').then(setSources).catch(() => {})
  }

  function reloadItems() {
    api(`/sources/${selectedSourceId}/items?limit=50`)
      .then(setItems)
      .catch((e) => say(e.message, true))
  }

  async function addSource() {
    try {
      const created = await api('/sources', {
        method: 'POST',
        body: JSON.stringify({ name: newName, url: newUrl, type: 'RSS' }),
      })

      setSources(await api('/sources'))
      setNewName('')
      setNewUrl('')
      setAddOpen(false)

      if (created.fetchJobId) {
        fetchJob.track(created.fetchJobId, created.name, created.id)
        say('')
      } else {
        say(`已新增「${created.name}」`)
      }
    } catch (e) {
      say(e.message, true)
    }
  }

  async function addPreset(feed) {
    setSubscribing(feed.url)
    say(`正在驗證「${feed.name}」…`)

    try {
      const created = await api('/sources', {
        method: 'POST',
        body: JSON.stringify({ name: feed.name, url: feed.url, type: 'RSS' }),
      })
      setSources(await api('/sources'))

      if (created.fetchJobId) {
        fetchJob.track(created.fetchJobId, feed.name, created.id)
        say('')
      } else {
        say(`已訂閱「${feed.name}」`)
      }
    } catch (e) {
      say(`「${feed.name}」訂閱失敗：${e.message}`, true)
    } finally {
      setSubscribing(null)
    }
  }

  async function fetchNow() {
    const source = sources.find((s) => s.id === selectedSourceId)

    try {
      const { jobId } = await api(`/sources/${selectedSourceId}/fetch`, { method: 'POST' })
      fetchJob.track(jobId, source?.name, selectedSourceId)
      say('')
    } catch (e) {
      say(e.message, true)
    }
  }

  async function openItem(id) {
    try {
      setDetail(await api(`/fetched-items/${id}`))
    } catch (e) {
      say(e.message, true)
    }
  }

  function closeReader() {
    setDetail(null)
    setReaderExpanded(false)
  }

  async function promote(id) {
    try {
      const doc = await api(`/fetched-items/${id}/promote`, { method: 'POST' })
      say(`已收藏（document #${doc.id}）`)
      closeReader()
      reloadItems()
      refreshSources()
    } catch (e) {
      say(e.message, true)
    }
  }

  async function resummarize(id) {
    try {
      await api(`/fetched-items/${id}/resummarize`, { method: 'POST' })
      say('已排入重新摘要，約 30 秒後更新')
      setDetail(null)
      reloadItems()
    } catch (e) {
      say(e.message, true)
    }
  }

  async function discard(id) {
    try {
      await api(`/fetched-items/${id}/discard`, { method: 'POST' })
      say('已丟棄')
      closeReader()
      reloadItems()
      refreshSources()
    } catch (e) {
      say(e.message, true)
    }
  }

  async function saveKey() {
    try {
      const me = await api('/me/llm-key', {
        method: 'PUT',
        body: JSON.stringify({ provider, apiKey }),
      })
      setKeyMasked(me.llmApiKeyMasked ?? '')
      setProvider(me.llmProvider ?? provider)
      setApiKey('')
      say(`API key 已儲存（${me.llmProvider}）`)
    } catch (e) {
      say(e.message, true)
    }
  }

  const themeToggle = (
      <button
          className="quiet"
          onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')}
          title={theme === 'dark' ? '切換到亮色' : '切換到暗色'}
          aria-label="切換主題"
      >
        {theme === 'dark' ? <SunIcon /> : <MoonIcon />}
      </button>
  )

  if (restoring) {
    return <div className="shell" />
  }

  if (!loggedIn) {
    return (
        <div className="shell">
          <div className="auth">
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'start' }}>
              <div>
                <span className="brand">Sift</span>
                <div className="auth-tagline">把訂閱的文章篩成自己的收藏庫</div>
              </div>
              {themeToggle}
            </div>

            <label>電子郵件</label>
            <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="you@example.com"
                autoComplete="username"
            />

            <label>密碼</label>
            <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="至少 8 碼"
                autoComplete="current-password"
            />

            <div className="auth-actions">
              <button className="primary" onClick={login}>登入</button>
              <button onClick={register}>註冊</button>
            </div>

            {error && <div className="notice error" style={{ marginTop: 20 }}>{error}</div>}
          </div>
        </div>
    )
  }

  return (
      <div className="shell">

        <header className="topbar">
          <span className="brand">Sift</span>

          <div className="topbar-actions">
            {themeToggle}

            <div className="user-menu">
            <button
                className="avatar-btn"
                onClick={() => setMenuOpen(!menuOpen)}
                aria-label="使用者選單"
            >
              <Avatar id={avatarId} size={34} />
            </button>

            {menuOpen && (
                <>
                  <div className="backdrop" onClick={() => setMenuOpen(false)} />

                  <div className="user-panel">
                    <div className="user-panel-email">{me?.email ?? '（載入中）'}</div>

                    <div className="user-panel-section">頭像</div>
                    <div className="avatar-grid">
                      {Array.from({ length: AVATAR_COUNT }, (_, i) => (
                          <button
                              key={i}
                              className={i === avatarId ? 'avatar-choice selected' : 'avatar-choice'}
                              onClick={() => setAvatarId(i)}
                              aria-label={`頭像 ${i + 1}`}
                          >
                            <Avatar id={i} size={44} ring={i === avatarId} />
                          </button>
                      ))}
                    </div>

                    <div className="user-panel-section">LLM API key</div>

                    <select
                        value={provider}
                        onChange={(e) => setProvider(e.target.value)}
                        style={{ marginBottom: 8 }}
                    >
                      {providers.map((p) => (
                          <option key={p} value={p}>
                            {p === 'GEMINI' ? 'Google Gemini' : p === 'FAKE' ? '假的（開發用）' : p}
                          </option>
                      ))}
                    </select>

                    <div className="field-row">
                      <input
                          type="password"
                          value={apiKey}
                          onChange={(e) => setApiKey(e.target.value)}
                          placeholder={keyMasked || '貼上 API key'}
                          autoComplete="off"
                      />
                      <button onClick={saveKey}>儲存</button>
                    </div>
                    <div className="field-hint" style={{ marginBottom: 4 }}>
                      {keyMasked
                          ? `目前：${keyMasked}`
                          : '尚未設定 — 文章不會產生摘要'}
                    </div>

                    <div className="user-panel-row">
                      <span style={{ fontSize: 13, color: 'var(--text-dim)' }}>
                        {sources.length} 個來源
                      </span>
                      <button className="quiet" onClick={logout}>登出</button>
                    </div>
                  </div>
                </>
            )}
            </div>
          </div>
        </header>

        <nav className="tabs">
          <button
              className={view === 'inbox' ? 'tab active' : 'tab'}
              onClick={() => { setView('inbox'); say('') }}
          >
            訂閱項目
          </button>
          <button
              className={view === 'library' ? 'tab active' : 'tab'}
              onClick={() => { setView('library'); say('') }}
          >
            收藏庫
          </button>

          <div style={{ flex: 1 }} />

          {view === 'inbox' && (
              <button className="quiet" onClick={() => setAddOpen(!addOpen)}>
                {addOpen ? '收起' : '＋ 新增訂閱'}
              </button>
          )}
        </nav>

        {addOpen && view === 'inbox' && (
            <section className="settings">
              <div className="field">
                <div className="field-label">自己貼網址</div>
                <div className="field-row">
                  <input
                      value={newName}
                      onChange={(e) => setNewName(e.target.value)}
                      placeholder="名稱"
                      style={{ maxWidth: 150 }}
                  />
                  <input
                      value={newUrl}
                      onChange={(e) => setNewUrl(e.target.value)}
                      placeholder="https://blog.rust-lang.org/feed.xml 或直接填首頁"
                  />
                  <button onClick={addSource}>新增</button>
                </div>
                <div className="field-hint">
                  填網站首頁也可以——後端會自己找出真正的 feed 網址
                </div>
              </div>

              <div className="field-label" style={{ marginTop: 24 }}>或從推薦的挑</div>

              {PRESET_FEEDS.map((group) => (
                  <div key={group.group} className="preset-group">
                    <div className="preset-group-name">{group.group}</div>

                    {group.items.map((feed) => {
                      const added = sources.some((s) => s.url === feed.url)

                      return (
                          <div key={feed.url} className="preset">
                            <div className="preset-info">
                              <div className="preset-name">{feed.name}</div>
                              <div className="preset-note">{feed.note}</div>
                            </div>

                            <button
                                disabled={added || subscribing !== null}
                                onClick={() => addPreset(feed)}
                            >
                              {added
                                  ? '已訂閱'
                                  : subscribing === feed.url ? '驗證中…' : '訂閱'}
                            </button>
                          </div>
                      )
                    })}
                  </div>
              ))}
            </section>
        )}

        {message && (
            <div className={messageIsError ? 'notice error' : 'notice'}>{message}</div>
        )}

        <FetchProgress job={fetchJob.job} onClose={fetchJob.clear} />

        {view === 'library' && <Library api={api} onError={(m) => say(m, true)} />}

        {view === 'inbox' && (
        <div className="columns">

          {!detail && (
          <aside className="col-sources">
            <div className="col-head">
              <span>
                訂閱來源
                {sources.length > 0 && <em className="count">{sources.length}</em>}
              </span>
            </div>

            {sources.length === 0 && (
                <div className="empty" style={{ padding: '32px 8px' }}>
                  還沒有來源
                  <span className="empty-hint">在上方貼一個 RSS 網址</span>
                </div>
            )}

            {sources.map((s) => (
                <div
                    key={s.id}
                    className={s.id === selectedSourceId ? 'source active' : 'source'}
                    onClick={() => setSelectedSourceId(s.id)}
                >
                  <div className="source-name">{s.name}</div>
                  <div className="source-url">{hostOf(s.url)}</div>

                  <div className="source-stats">
                    <span>{s.itemCount} 篇</span>
                    {s.readyCount > 0 && (
                        <span className="source-ready">{s.readyCount} 則待讀</span>
                    )}
                  </div>
                </div>
            ))}
          </aside>
          )}

          <main className={detail ? 'col-items narrow' : 'col-items'}>
            <div className="col-head">
              <span>
                抓到的文章
                {sortedItems.length > 0 && (
                    <em className="count">{sortedItems.length}</em>
                )}
              </span>

              <div className="col-head-actions">
                {decidedCount > 0 && (
                    <button
                        className="quiet"
                        onClick={() => setShowDecided(!showDecided)}
                        title={showDecided ? '收起已處理的文章' : '顯示收藏過與丟棄過的文章'}
                    >
                      {showDecided ? '隱藏已處理' : `已處理 ${decidedCount}`}
                    </button>
                )}

                {selectedSourceId && (
                    <button onClick={fetchNow} disabled={fetchingSelected}>
                      {fetchingSelected ? '抓取中…' : '立刻抓取'}
                    </button>
                )}
              </div>
            </div>

            {!selectedSourceId && (
                <div className="empty">
                  {sources.length === 0 ? '還沒有訂閱任何來源' : '選一個來源開始讀'}
                  <span className="empty-hint">
                    {sources.length === 0
                        ? '在上方貼一個 RSS 網址，例如 https://blog.rust-lang.org/feed.xml'
                        : '左邊的清單點一下就好'}
                  </span>
                </div>
            )}

            {selectedSourceId && items.length === 0 && (
                <div className="empty">
                  這個來源還沒有文章
                  <span className="empty-hint">
                    排程是每天早上 6 點抓一次。想現在就看，按右上角的「立刻抓取」。
                  </span>
                </div>
            )}

            {selectedSourceId && items.length > 0 && sortedItems.length === 0 && (
                <div className="empty">
                  這個來源的文章都處理完了
                  <span className="empty-hint">
                    {decidedCount} 篇已經收藏或丟棄。按右上角「已處理 {decidedCount}」可以回頭看。
                  </span>
                </div>
            )}

            {sortedItems.map((item) => (
                <article
                    key={item.id}
                    className={detail?.id === item.id ? 'item reading' : 'item'}
                    onClick={() => openItem(item.id)}
                >
                  <div className="item-head">
                    <h3 className="item-title">{item.title}</h3>
                    <span className={statusClass(item.status)}>{item.status}</span>
                  </div>

                  <div className="item-meta">
                    {item.publishedAt && <span>{relativeDate(item.publishedAt)}</span>}
                    <span className="item-host">{hostOf(item.externalUrl)}</span>
                  </div>

                  {item.summary && <div className="item-summary">{item.summary}</div>}

                  {item.failureReason && (
                      <div className="item-summary" style={{ color: 'var(--danger)' }}>
                        {item.failureReason}
                      </div>
                  )}

                  {item.status === 'READY' && !detail && (
                      <div className="item-actions" onClick={(e) => e.stopPropagation()}>
                        <button className="primary" onClick={() => promote(item.id)}>收藏</button>
                        <button className="quiet" onClick={() => discard(item.id)}>丟掉</button>
                      </div>
                  )}
                </article>
            ))}
          </main>

          {detail && (
              <Reader
                  item={detail}
                  expanded={readerExpanded}
                  onToggle={() => setReaderExpanded(!readerExpanded)}
                  onClose={closeReader}
                  onPromote={promote}
                  onDiscard={discard}
                  onResummarize={resummarize}
              />
          )}
        </div>
        )}
      </div>
  )
}

function relativeDate(iso) {
  const then = new Date(iso)
  const days = Math.floor((Date.now() - then.getTime()) / 86400000)

  if (days < 0) return then.toLocaleDateString('zh-TW')
  if (days === 0) return '今天'
  if (days === 1) return '昨天'
  if (days < 30) return `${days} 天前`

  return then.toLocaleDateString('zh-TW')
}

function hostOf(url) {
  try {
    return new URL(url).hostname
  } catch {
    return ''
  }
}

function statusClass(status) {
  if (status === 'READY') return 'status ready'
  if (status === 'FAILED') return 'status failed'
  return 'status'
}

function SunIcon() {
  return (
      <svg width="15" height="15" viewBox="0 0 24 24" fill="none"
           stroke="currentColor" strokeWidth="1.8" strokeLinecap="round">
        <circle cx="12" cy="12" r="4" />
        <path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
      </svg>
  )
}

function MoonIcon() {
  return (
      <svg width="15" height="15" viewBox="0 0 24 24" fill="none"
           stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
        <path d="M21 12.8A9 9 0 1111.2 3a7 7 0 009.8 9.8z" />
      </svg>
  )
}

export default App
