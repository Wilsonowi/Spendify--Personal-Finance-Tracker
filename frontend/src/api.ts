const API_ROOT = '/api'

function csrfToken() {
  return document.cookie
    .split('; ')
    .find((entry) => entry.startsWith('XSRF-TOKEN='))
    ?.split('=')
    .slice(1)
    .join('=')
}

export async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers)
  if (options.body && !(options.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json')
  }
  const method = (options.method ?? 'GET').toUpperCase()
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    const token = csrfToken()
    if (token) headers.set('X-XSRF-TOKEN', decodeURIComponent(token))
  }
  const response = await fetch(`${API_ROOT}${path}`, {
    ...options,
    headers,
    credentials: 'include',
    cache: 'no-store',
  })
  if (!response.ok) {
    const problem = await response.json().catch(() => null)
    throw new Error(problem?.detail ?? `Request failed with status ${response.status}`)
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

export async function initializeCsrf() {
  await api<{ token: string }>('/auth/csrf')
}

export async function downloadDocument(documentId: string) {
  const response = await fetch(`${API_ROOT}/imports/${documentId}/content`, {
    credentials: 'include',
    cache: 'no-store',
  })
  if (!response.ok) throw new Error('Document preview is unavailable.')
  return response.blob()
}

export async function downloadDocumentPage(documentId: string, page: number) {
  const response = await fetch(`${API_ROOT}/imports/${documentId}/pages/${page}`, {
    credentials: 'include',
    cache: 'no-store',
  })
  if (!response.ok) throw new Error('Statement page preview is unavailable.')
  return response.blob()
}
