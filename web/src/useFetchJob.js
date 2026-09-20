/*
 * Polls one fetch job until it finishes.
 *
 * Chained setTimeout rather than setInterval, so a slow server cannot stack
 * requests on top of each other, and with an attempt cap, because polling
 * without a stop condition is just a slow infinite loop.
 */

import { useEffect, useRef, useState } from 'react'

const INTERVAL_MS = 2000

const MAX_ATTEMPTS = 90

export function useFetchJob(api, onFinished) {
  const [job, setJob] = useState(null)

  // Held in a ref: the effect needs the latest callback without re-running
  // every render, which would clear the timer before it ever fires.
  const onFinishedRef = useRef(onFinished)
  onFinishedRef.current = onFinished

  const jobId = job?.id ?? null
  const finished = job?.finished ?? false

  useEffect(() => {
    if (!jobId || finished) return

    let cancelled = false
    let attempts = 0
    let timer

    async function poll() {
      attempts += 1

      let next
      try {
        next = await api(`/fetch-jobs/${jobId}`)
      } catch (e) {
        if (cancelled) return
        setJob((j) => ({
          ...j,
          status: 'FAILED',
          finished: true,
          failureReason: e.message,
        }))
        return
      }

      if (cancelled) return

      setJob((j) => ({ ...next, label: j?.label, sourceId: j?.sourceId }))

      if (next.finished) {
        onFinishedRef.current?.(next)
        return
      }

      if (attempts >= MAX_ATTEMPTS) {
        setJob((j) => ({
          ...j,
          status: 'FAILED',
          finished: true,
          failureReason: '等太久了，去「抓取紀錄」看看後來怎麼了',
        }))
        return
      }

      timer = setTimeout(poll, INTERVAL_MS)
    }

    timer = setTimeout(poll, INTERVAL_MS)

    return () => {
      cancelled = true
      clearTimeout(timer)
    }
  }, [jobId, finished])

  return {
    job,

    track(id, label, sourceId) {
      setJob({ id, status: 'PENDING', finished: false, newItemCount: 0, label, sourceId })
    },

    clear() {
      setJob(null)
    },
  }
}
