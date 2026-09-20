/*
 * Fetch progress strip.
 *
 * A pulsing dot rather than a progress bar: how long a fetch takes depends on
 * someone else's server, so any percentage would be invented.
 */

export default function FetchProgress({ job, onClose }) {
  if (!job) return null

  const { status, finished, newItemCount, failureReason, label } = job

  const text = {
    PENDING: '排隊中…',
    RUNNING: '抓取中…',
  }[status]

  return (
      <div className={finished && status === 'FAILED' ? 'progress failed' : 'progress'}>

        {!finished && <span className="progress-dot" />}

        <span className="progress-text">
          {label && <strong>{label}</strong>}

          {!finished && text}

          {finished && status === 'SUCCESS' && (
              newItemCount > 0
                  ? `抓到 ${newItemCount} 篇新文章`
                  : '沒有新文章（這個來源最近沒更新）'
          )}

          {finished && status === 'FAILED' && `抓取失敗：${failureReason}`}
        </span>

        {finished && (
            <button className="quiet progress-close" onClick={onClose}>✕</button>
        )}
      </div>
  )
}
