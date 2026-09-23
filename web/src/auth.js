/*
 * Where the tokens live on the page side.
 *
 * The access token is kept in a module variable: memory only, never written to
 * storage. Closing the tab loses it, which is fine because it only lives for
 * 15 minutes anyway. The refresh token is not here at all; the server sets it
 * as an HttpOnly cookie that page scripts cannot read, and the browser attaches
 * it to /api/v1/auth requests by itself.
 *
 * Refreshes are shared: if several requests hit 401 at once (or React runs an
 * effect twice in development), they all wait on the same refresh. Two separate
 * refreshes would present the same refresh token twice, and the server treats
 * that as theft and revokes every session.
 */

let accessToken = ''

let refreshInFlight = null

export function getAccessToken() {
  return accessToken
}

export function setAccessToken(token) {
  accessToken = token ?? ''
}

export function refreshAccessToken() {
  if (!refreshInFlight) {
    refreshInFlight = fetch('/api/v1/auth/refresh', {
      method: 'POST',
      credentials: 'same-origin',
    })
      .then(async (res) => {
        if (!res.ok) return null
        const data = await res.json()
        accessToken = data.accessToken
        return accessToken
      })
      .catch(() => null)
      .finally(() => {
        refreshInFlight = null
      })
  }

  return refreshInFlight
}
