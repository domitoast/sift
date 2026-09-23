/*
 * Knowledge base: the articles that were promoted, with search and paging.
 *
 * Paging is left to the server. Filtering a page client-side would silently
 * show the wrong page size.
 */

import { useEffect, useState } from 'react'

export default function Library({ api, onError }) {
  const [page, setPage] = useState(0)
  const [data, setData] = useState(null)
  const [keyword, setKeyword] = useState('')

  const [query, setQuery] = useState('')

  const [selected, setSelected] = useState(null)

  useEffect(() => {
    const params = new URLSearchParams({ page, size: 20 })
    if (query) params.set('keyword', query)

    api(`/documents?${params}`)
        .then(setData)
        .catch((e) => onError(e.message))
  }, [page, query])

  async function open(id) {
    try {
      setSelected(await api(`/documents/${id}`))
    } catch (e) {
      onError(e.message)
    }
  }

  async function remove(id) {
    try {
      await api(`/documents/${id}`, { method: 'DELETE' })
      setSelected(null)

      setPage((p) => p)
      const params = new URLSearchParams({ page, size: 20 })
      if (query) params.set('keyword', query)
      setData(await api(`/documents?${params}`))
    } catch (e) {
      onError(e.message)
    }
  }

  if (!data) {
    return <div className="empty">載入中…</div>
  }

  return (
      <div className="columns">

        <main className={selected ? 'col-items narrow' : 'col-items'}>

          <div className="col-head">
            <span>
              收藏庫
              {data.totalElements > 0 && <em className="count">{data.totalElements}</em>}
            </span>
          </div>

          <div className="field-row" style={{ margin: '14px 0 4px', maxWidth: 420 }}>
            <input
                value={keyword}
                onChange={(e) => setKeyword(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') {
                    setQuery(keyword)
                    setPage(0)
                  }
                }}
                placeholder="搜尋標題或內容，按 Enter"
            />
            {query && (
                <button className="quiet" onClick={() => { setKeyword(''); setQuery(''); setPage(0) }}>
                  清除
                </button>
            )}
          </div>

          {data.content.length === 0 && (
              <div className="empty">
                {query ? '沒有符合的文件' : '收藏庫還是空的'}
                <span className="empty-hint">
                  {query
                      ? '換個關鍵字試試'
                      : '到「訂閱項目」把想留下來的文章按「收藏」'}
                </span>
              </div>
          )}

          {data.content.map((doc) => (
              <article
                  key={doc.id}
                  className={selected?.id === doc.id ? 'item reading' : 'item'}
                  onClick={() => open(doc.id)}
              >
                <div className="item-head">
                  <h3 className="item-title">{doc.title}</h3>
                  <span className="status">
                    {doc.origin === 'FETCHED' ? '訂閱' : '手寫'}
                  </span>
                </div>
                <div className="item-meta">
                  <span>收藏於 {new Date(doc.createdAt).toLocaleDateString('zh-TW')}</span>
                </div>
              </article>
          ))}

          {data.totalPages > 1 && (
              <div className="pager">
                <button disabled={page === 0} onClick={() => setPage(page - 1)}>上一頁</button>
                <span className="pager-info">{page + 1} / {data.totalPages}</span>
                <button disabled={!data.hasNext} onClick={() => setPage(page + 1)}>下一頁</button>
              </div>
          )}
        </main>

        {selected && (
            <div className="reader">
              <div className="reader-bar">
                <span className="reader-source">收藏的文件</span>
                <div className="reader-bar-right">
                  <button className="quiet" onClick={() => remove(selected.id)}>刪除</button>
                  <button className="quiet" onClick={() => setSelected(null)}>✕</button>
                </div>
              </div>

              <div className="reader-body">
                <h1 className="reader-title">{selected.title}</h1>

                <div className="reader-meta">
                  <span>{selected.origin === 'FETCHED' ? '來自訂閱' : '手寫筆記'}</span>
                  <span>{new Date(selected.createdAt).toLocaleDateString('zh-TW')}</span>
                </div>

                <div className="reader-text" style={{ whiteSpace: 'pre-wrap' }}>
                  {selected.content}
                </div>
              </div>
            </div>
        )}
      </div>
  )
}
