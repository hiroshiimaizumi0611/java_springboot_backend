import axios from 'axios'

const MAINTENANCE_URL = 'https://estimate-app-sorrypage.s3.ap-northeast-1.amazonaws.com/index.html'

export const api = axios.create({
  baseURL: '/api/',
  withCredentials: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
})

// CSRF ヘルパ: XSRF-TOKEN Cookie が無ければ取得
async function ensureCsrf() {
  const has = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]+)/)
  if (!has) {
    // same-origin 前提。もし別オリジン構成の場合は 'include' に変更してCORS対応が必要
    await fetch('/api/csrf', { credentials: 'same-origin' })
  }
}

// refresh 併発制御（多重401発生時に1回だけ実行）
let refreshPromise: Promise<void> | null = null

// Authorization ヘッダは使用せず、Cookie (AT) を送信
api.interceptors.response.use(
  res => res,
  async err => {
    const res = err?.response as { status?: number } | undefined
    const cfg = (err?.config || {}) as any

    // 503: メンテナンス誘導
    if (res?.status === 503) {
      window.location.href = MAINTENANCE_URL
      return Promise.reject(err)
    }

    // リフレッシュ自身は対象外（無限ループ防止）
    const url: string = (cfg?.url || '')
    if (url.includes('/auth/refresh')) {
      return Promise.reject(err)
    }

    // 401 を1回だけリトライ（refresh → 元リクエスト再実行）
    if (res?.status === 401 && !cfg.__retried) {
      cfg.__retried = true
      try {
        if (!refreshPromise) {
          refreshPromise = (async () => {
            await ensureCsrf()
            // xsrfCookieName/xsrfHeaderName 設定により axios が自動でヘッダ付与
            try {
              await api.post('auth/refresh')
            } catch (e: any) {
              // 稀にトークン不一致などで 403 が発生した場合、再取得して一度だけ再試行
              if (e?.response?.status === 403) {
                await ensureCsrf()
                await api.post('auth/refresh')
              } else {
                throw e
              }
            }
          })()
        }
        await refreshPromise
        refreshPromise = null
        return api(cfg)
      } catch (e) {
        refreshPromise = null
        // refresh も失敗 → ログイン導線へ
        window.location.href = '/oauth2/authorization/azure'
        return Promise.reject(e)
      }
    }

    return Promise.reject(err)
  },
)
