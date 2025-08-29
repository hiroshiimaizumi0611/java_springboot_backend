# フロントエンド実装ガイド（React + Nginx）

本ドキュメントは、現行バックエンドの認証/認可フロー（OAuth2/OIDC + 自前JWT + Cookieベース + CSRF有効）に対して、フロントエンド側に期待する実装をまとめたものです。SPA は同一オリジン想定で、Nginx リバースプロキシ経由で Spring Boot に到達します。

## 前提と概要
- 同一オリジン: フロント（`/`）と API（`/api` など）は同じオリジン配下。CORS を使わずにクッキーを利用。
- 認可方式: OAuth2/OIDC ログイン開始 `GET /oauth2/authorization/{registrationId}` → コールバック `/login/oauth2/code/{registrationId}`（バックエンドが処理）。
- アクセストークン: JWT（HS256）。クライアントJSからは不可視（`HttpOnly` Cookie `access_token`）。
- 表示用情報: 非署名の `user_info` Cookie（Base64URL JSON）。UI 初期レンダに利用可（安全判断には使わない）。
- セッション状態: Redis（Spring Session）で `sid, ver, lastSeen` 管理。`ver` 不一致時は失効。
- CSRF: 有効。`X-XSRF-TOKEN` ヘッダ必須。`GET /api/csrf` で取得（または `XSRF-TOKEN` Cookie を読む）。
- 主要 API: `POST /api/auth/refresh`（204）、`POST /api/auth/logout`（204）、`GET /api/csrf`（CSRF取得）。

## クッキー仕様（復習）
- `access_token`: HttpOnly; SameSite=Lax; Path=/; Secure=true(prod)/false(local)
- `user_info`: 非HttpOnly; SameSite=Lax; Path=/; Max-Age≈10m; Secure=true/false（Base64URL JSON）

JS は `access_token` を一切読み取らず、`withCredentials`/`credentials: 'include'` で送信のみ行う。

## 推奨フロー
1) 初回ロード
- `user_info` Cookie があればデコードして一旦 UI に表示（暫定）。
- 事前に `GET /api/csrf` を1度実行し、`XSRF-TOKEN` Cookie を確実に取得。
- 確認用の `/api/me` は廃止。必要なときに各機能 API を呼び、401 を検知してリフレッシュ/ログイン誘導する。

2) ログイン開始
- ボタン等から `window.location.assign('/oauth2/authorization/cognito')`（dev）/`.../azure`（prod）へ遷移。
- バックエンドが Cookie を設定してルート（例 `/`）にリダイレクト。

3) API 呼び出し
- `fetch`/`axios` は常に `credentials: 'include'`/`withCredentials: true`。
- 変更系（POST/PUT/PATCH/DELETE）は `X-XSRF-TOKEN` ヘッダを付与（`XSRF-TOKEN` Cookie 値）。

4) リフレッシュ
- API が 401 を返したら一度だけ `POST /api/auth/refresh` を実行し、成功したら元リクエストをリトライ。
- 追加で、任意に 8 分間隔程度のサイレントリフレッシュを実行（ページがアクティブなときのみ）。

5) ログアウト
- `POST /api/auth/logout`（CSRF ヘッダ必須）。204 ならクライアントの状態をクリアしトップへ遷移。
- 別タブでのログアウト（`ver++`）により 401 が返る場合もあるので、401 はログイン誘導にフォールバック。

## React 実装の要点
- トークンは一切ストレージに保存しない（Cookie のみ）。
- `user_info` は UI の初期表示最適化にのみ使用（権限判断はサーバ応答に従う）。
- 例外（401/403/419 相当）は共通ハンドラで扱い、CSRF 再取得→リトライ→ログイン誘導の順で処理。

### Cookie/CSRF ユーティリティ例
```ts
// src/lib/auth.ts
export function getCookie(name: string): string | null {
  const m = document.cookie.match(new RegExp('(^|; )' + name.replace(/([.$?*|{}()\[\]\\\/\+^])/g, '\\$1') + '=([^;]*)'));
  return m ? decodeURIComponent(m[2]) : null;
}

export function base64UrlDecodeJson<T = unknown>(val: string): T | null {
  try {
    const pad = (s: string) => s + '='.repeat((4 - (s.length % 4)) % 4);
    const json = atob(pad(val).replace(/-/g, '+').replace(/_/g, '/'));
    return JSON.parse(json) as T;
  } catch {
    return null;
  }
}

export type UserInfo = { sub?: string; name?: string; email?: string; roles?: string[]; [k: string]: unknown };

export function readUserInfo(): UserInfo | null {
  const raw = getCookie('user_info');
  if (!raw) return null;
  return base64UrlDecodeJson<UserInfo>(raw);
}

export async function ensureCsrf(): Promise<string | null> {
  // Cookie に既にあるならそれを使用
  let token = getCookie('XSRF-TOKEN');
  if (token) return token;
  // ない場合は取得（バックエンドが Cookie を設定）
  await fetch('/api/csrf', { credentials: 'include' });
  token = getCookie('XSRF-TOKEN');
  return token;
}
```

### Axios ラッパ例（推奨）
```ts
// src/lib/api.ts
import axios, { AxiosError } from 'axios';
import { ensureCsrf, getCookie } from './auth';

export const api = axios.create({ baseURL: '/api', withCredentials: true });

api.interceptors.request.use(async (config) => {
  const method = (config.method || 'get').toLowerCase();
  if (['post', 'put', 'patch', 'delete'].includes(method)) {
    const token = getCookie('XSRF-TOKEN') || (await ensureCsrf());
    if (token) {
      config.headers = { ...(config.headers || {}), 'X-XSRF-TOKEN': token };
    }
  }
  return config;
});

api.interceptors.response.use(undefined, async (error: AxiosError) => {
  const res = error.response;
  const original: any = error.config || {};

  // CSRF 欠落等の 403 対応（必要に応じて）
  if (res?.status === 403 && !original._retried) {
    original._retried = true;
    await ensureCsrf();
    return api.request(original);
  }

  // 401 は一度だけリフレッシュ→リトライ
  if (res?.status === 401 && !original._retried) {
    original._retried = true;
    try {
      await ensureCsrf();
      await api.post('/auth/refresh');
      return api.request(original);
    } catch (e) {
      // 失敗したらログイン誘導
      const to = (window as any).__REG_ID__ || 'cognito';
      window.location.assign(`/oauth2/authorization/${to}`);
      return Promise.reject(e);
    }
  }

  return Promise.reject(error);
});

export async function logout(): Promise<void> {
  await ensureCsrf();
  await api.post('/auth/logout');
  window.location.replace('/');
}
```

### 起動時ブートストラップ例
```ts
// src/main.tsx（抜粋）
import { createRoot } from 'react-dom/client';
import App from './App';
import { ensureCsrf, readUserInfo } from './lib/auth';

async function bootstrap() {
  // 先に CSRF を確保
  await ensureCsrf();

  // 暫定表示
  const cached = readUserInfo();
  // ここで cached を状態に流しておけば初期描画が速い

  // 実 API は各画面で初回アクセス時に呼び、401 は axios インターセプタで
  // refresh→（失敗時）ログイン誘導にフォールバックする。

  // 任意：アクティブ時のみサイレントリフレッシュ
  const tick = async () => {
    if (document.hidden) return;
    try {
      await ensureCsrf();
      await api.post('/auth/refresh');
    } catch {
      /* 無視（401なら次のAPIで誘導） */
    }
  };
  const id = window.setInterval(tick, 8 * 60 * 1000);
  window.addEventListener('visibilitychange', tick);

  createRoot(document.getElementById('root')!).render(<App />);
}

bootstrap();
```

## Nginx 設定例（同一オリジン）
```nginx
# 例: /etc/nginx/conf.d/app.conf
upstream backend {
  server backend:8080; # docker-compose などのサービス名
}

server {
  listen 80;
  server_name _;

  root /usr/share/nginx/html; # React ビルド配置先
  index index.html;

  # セキュリティ系（適宜調整）
  add_header X-Frame-Options SAMEORIGIN;
  add_header X-Content-Type-Options nosniff;

  # API / OAuth 経路はバックエンドに委譲
  location ~ ^/(api|oauth2|login|logout|error|actuator) {
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme; # TLS終端が別なら https を固定
    proxy_pass http://backend;
  }

  # SPA ルーティング
  location / {
    try_files $uri /index.html;
  }
}
```
- 本番で TLS 終端が Nginx の場合は `listen 443 ssl;` の設定と共に `proxy_set_header X-Forwarded-Proto https;` を明示。
- 同一オリジン運用により、`withCredentials` だけで Cookie が送信される（CORS 設定は不要）。

## エラーとフォールバック
- 401: リフレッシュを一度試行→失敗時はログイン開始へ遷移。
- 403（CSRF 不足）: `GET /api/csrf` でトークン取得→リトライ。
- 多タブ: どれかのタブでログアウト→他タブは 401 になり次第ログイン誘導。任意で `BroadcastChannel` による連携を追加可能。

## セキュリティ注意点
- `access_token` は HttpOnly のためクライアントから閲覧・保存しない。
- `user_info` は非署名。UI 利便のみで使用し、権限判断はサーバ応答に従う。
- ローカル開発は HTTP（Secure=false）、本番は HTTPS（Secure=true）。`X-Forwarded-Proto` が正しく届くよう Nginx を設定。

## 動作確認チェックリスト
- [ ] `/oauth2/authorization/{registrationId}` でログイン開始→トップへ戻る。
- [ ] `GET /api/csrf` 後に `XSRF-TOKEN` Cookie が存在。
- [ ] 401 から `POST /api/auth/refresh`（CSRF ヘッダあり）で 204→元のリクエスト再送。
- [ ] `POST /api/auth/logout`（CSRF ヘッダあり）で 204→Cookie が削除される。
- [ ] Nginx 経由で `Set-Cookie` が欠落しない（ヘッダ透過）。

---
このガイドに沿って、フロントは「Cookie 送信 + CSRF ヘッダ付与 + 401 時リフレッシュ + ログイン誘導」の 4 点を実装すれば、バックエンドの新仕様と整合します。
