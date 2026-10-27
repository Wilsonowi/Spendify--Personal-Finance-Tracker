import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from './api'

describe('api', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/'
  })

  it('sends credentials and CSRF token for writes', async () => {
    document.cookie = 'XSRF-TOKEN=test-token; path=/'
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ ok: true }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    }))
    vi.stubGlobal('fetch', fetchMock)

    await api('/transactions', { method: 'POST', body: JSON.stringify({ amount: 12.5 }) })

    expect(fetchMock).toHaveBeenCalledWith('/api/transactions', expect.objectContaining({
      credentials: 'include',
      cache: 'no-store',
    }))
    const request = fetchMock.mock.calls[0][1]
    expect(request.headers.get('X-XSRF-TOKEN')).toBe('test-token')
  })
})
