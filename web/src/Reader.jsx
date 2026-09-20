/*
 * Article reader. Split-pane or full-width, with adjustable type, leading,
 * measure and paper colour.
 */

import { useEffect, useState } from 'react'

const FONT_SIZES = [15, 17, 19, 21, 24, 28]
const LINE_HEIGHTS = [1.6, 1.8, 2.0, 2.3, 2.6]

const WIDTHS = [
  { label: '窄', px: 760 },
  { label: '中', px: 980 },
  { label: '寬', px: 1240 },
]

const PAPERS = [
  { id: 'theme', label: '跟隨主題', bg: null, fg: null },
  { id: 'paper', label: '紙', bg: '#f4ecd8', fg: '#3b3226' },
  { id: 'night', label: '夜', bg: '#111417', fg: '#b9bcc2' },
]

export default function Reader({ item, expanded, onToggle, onClose,
                                onPromote, onDiscard, onResummarize }) {
  const [sizeIdx, setSizeIdx] = useLocalNumber('sift_reader_size', 1)
  const [lineIdx, setLineIdx] = useLocalNumber('sift_reader_line', 2)
  const [serif, setSerif] = useLocalNumber('sift_reader_serif', 0)
  const [paperIdx, setPaperIdx] = useLocalNumber('sift_reader_paper', 0)
  const [widthIdx, setWidthIdx] = useLocalNumber('sift_reader_width', 1)

  const paper = PAPERS[paperIdx] ?? PAPERS[0]
  const width = (WIDTHS[widthIdx] ?? WIDTHS[1]).px

  const paragraphs = (item.rawContent ?? '')
      .split(/\n{2,}/)
      .map((p) => p.trim())
      .filter(Boolean)

  useEffect(() => {
    if (!expanded) return

    function onKey(e) {
      if (e.key === 'Escape') onToggle()
    }

    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [expanded, onToggle])

  const contentStyle = {
    fontSize: FONT_SIZES[sizeIdx],
    lineHeight: LINE_HEIGHTS[lineIdx],
    fontFamily: serif ? 'var(--serif)' : 'var(--sans)',
    color: paper.fg ?? 'var(--text)',
  }

  return (
      <div
          className={expanded ? 'reader expanded' : 'reader'}
          style={{
            ...(paper.bg ? { background: paper.bg } : {}),

            '--reader-width': width + 'px',
          }}
      >

        <div className="reader-bar">
          <div className="reader-bar-left">
            <button className="quiet" onClick={onToggle}>
              {expanded ? <ShrinkIcon /> : <ExpandIcon />}
            </button>
            <span className="reader-source" style={{ color: paper.fg ?? undefined }}>
              {expanded ? '按 Esc 縮小' : '閱讀'}
            </span>
          </div>

          <div className="reader-bar-right">
            {(item.status === 'READY' || item.status === 'FAILED') && (
                <button className="quiet" onClick={() => onResummarize(item.id)}>
                  重新摘要
                </button>
            )}

            {item.status === 'READY' && (
                <>
                  <button className="primary" onClick={() => onPromote(item.id)}>收藏</button>
                  <button className="quiet" onClick={() => onDiscard(item.id)}>丟掉</button>
                </>
            )}
            <button className="quiet" onClick={onClose}><CloseIcon /></button>
          </div>
        </div>

        <div className="reader-tools">
          <Stepper
              label="字級"
              onDown={() => setSizeIdx(Math.max(0, sizeIdx - 1))}
              onUp={() => setSizeIdx(Math.min(FONT_SIZES.length - 1, sizeIdx + 1))}
              value={FONT_SIZES[sizeIdx]}
              fg={paper.fg}
          />

          <Stepper
              label="行距"
              onDown={() => setLineIdx(Math.max(0, lineIdx - 1))}
              onUp={() => setLineIdx(Math.min(LINE_HEIGHTS.length - 1, lineIdx + 1))}
              value={LINE_HEIGHTS[lineIdx].toFixed(1)}
              fg={paper.fg}
          />

          <div className="tool-group">
            <span className="tool-label" style={{ color: paper.fg ?? undefined }}>字體</span>
            <button className="tool-btn" onClick={() => setSerif(serif ? 0 : 1)}>
              {serif ? '明體' : '黑體'}
            </button>
          </div>

          {expanded && (
              <div className="tool-group">
                <span className="tool-label" style={{ color: paper.fg ?? undefined }}>版面</span>
                {WIDTHS.map((w, i) => (
                    <button
                        key={w.label}
                        className={i === widthIdx ? 'tool-btn selected' : 'tool-btn'}
                        onClick={() => setWidthIdx(i)}
                    >
                      {w.label}
                    </button>
                ))}
              </div>
          )}

          <div className="tool-group">
            <span className="tool-label" style={{ color: paper.fg ?? undefined }}>紙色</span>
            {PAPERS.map((p, i) => (
                <button
                    key={p.id}
                    className={i === paperIdx ? 'paper-dot selected' : 'paper-dot'}
                    style={{ background: p.bg ?? 'var(--bg)' }}
                    onClick={() => setPaperIdx(i)}
                    title={p.label}
                    aria-label={p.label}
                />
            ))}
          </div>
        </div>

        <div className="reader-body">
          <h1 className="reader-title" style={{
            fontFamily: contentStyle.fontFamily,
            color: paper.fg ?? 'var(--text-strong)',
          }}>
            {item.title}
          </h1>

          <div className="reader-meta">
            <a href={item.externalUrl} target="_blank" rel="noopener noreferrer">
              原文連結
            </a>
            {item.publishedAt && (
                <span>{new Date(item.publishedAt).toLocaleDateString('zh-TW')}</span>
            )}
          </div>

          {item.summary && (
              <div className="reader-summary" style={{ color: paper.fg ?? undefined }}>
                {item.summary}
              </div>
          )}

          <div className="reader-text" style={contentStyle}>
            {paragraphs.length === 0 && (
                <p style={{ textIndent: 0, color: 'var(--text-dim)' }}>
                  （這個 feed 沒有提供內文，點上方的原文連結去看）
                </p>
            )}

            {paragraphs.map((p, i) => <p key={i}>{p}</p>)}
          </div>
        </div>
      </div>
  )
}

function Stepper({ label, value, onDown, onUp, fg }) {
  return (
      <div className="tool-group">
        <span className="tool-label" style={{ color: fg ?? undefined }}>{label}</span>
        <button className="tool-btn" onClick={onDown}>−</button>
        <span className="tool-value" style={{ color: fg ?? undefined }}>{value}</span>
        <button className="tool-btn" onClick={onUp}>＋</button>
      </div>
  )
}

function useLocalNumber(key, fallback) {
  const [value, setValue] = useState(() => {
    const saved = localStorage.getItem(key)
    return saved === null ? fallback : Number(saved)
  })

  useEffect(() => {
    localStorage.setItem(key, String(value))
  }, [key, value])

  return [value, setValue]
}

function ExpandIcon() {
  return (
      <svg width="15" height="15" viewBox="0 0 24 24" fill="none"
           stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
        <path d="M15 3h6v6M9 21H3v-6M21 3l-7 7M3 21l7-7" />
      </svg>
  )
}

function ShrinkIcon() {
  return (
      <svg width="15" height="15" viewBox="0 0 24 24" fill="none"
           stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
        <path d="M9 3v6H3M15 21v-6h6M3 9l7-7M21 15l-7 7" />
      </svg>
  )
}

function CloseIcon() {
  return (
      <svg width="15" height="15" viewBox="0 0 24 24" fill="none"
           stroke="currentColor" strokeWidth="1.8" strokeLinecap="round">
        <path d="M6 6l12 12M18 6L6 18" />
      </svg>
  )
}
