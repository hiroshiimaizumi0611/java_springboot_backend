# 認証ユースケース フロー（簡素構成）

登場要素: Browser、Backend（Controller/Filter/Service）、Spring Security（OAuth2）、Redis（Spring Session/自前セッションメタ）、IdP。

## ログイン（成功）
- Browser → Backend: `GET /oauth2/authorization/{registrationId}`（ログイン開始）
- Backend → IdP: リダイレクト
- IdP → Backend: `GET /login/oauth2/code/{registrationId}`（認証コード）
- Spring Security: 認証コード交換 → ユーザ情報取得
- `OAuth2LoginSuccessHandler`:
  - 端末セッション生成（`sid/ver`）
  - 自前 AT 発行 → `Set-Cookie: AT`
  - UI クッキー発行（署名なし） → `Set-Cookie: UI`
  - HttpSession（Spring Session/Redis）へ Authorized Client（IdP AT/RT）と `sid/ver/uid/displayName` を保存（principalName も保持）
  - ルート `/` へリダイレクト
- Browser: 以後 `AT` を送信

## API 呼び出し
- Browser → Backend: 任意の `GET/POST /api/**`（Cookie: `AT`）
- `AtCookieAuthenticationFilter`:
  - 自前 AT の検証（署名/exp）
  - Redis `sess:{sid}` と照合（`ver` 一致、`lastSeen` 更新、120分アイドル判定）
  - OK → SecurityContext に認証セット
  - NG（ver 不一致/アイドル超過, refresh 以外）→ `ver++` ＋ `AT/UI` クリア
  - NG（JWT 無効, refresh 以外）→ `AT/UI` クリア（sid 不明のため `ver++` なし）
- Controller/Service: 正常処理 → 200

## AT リフレッシュ
- Browser → Backend: `POST /api/auth/refresh`（CSRF ヘッダ必須）
- Controller → `AuthRefreshService.refresh` へ委譲:
  - HttpSession（Redis）から `sid/ver/uid/displayName` 取得
  - Redis の `sess:{sid}.ver` と HttpSession の `ver` を照合（不一致なら 401・`AT/UI` クリア）
  - 一致時のみ `OAuth2AuthorizedClientManager.authorize(...)` 実行
    - 必要に応じて IdP の RT で Authorized Client を更新（HttpSession へ保存）
    - 失敗 → `AT/UI` クリア ＋ 401
  - 自前 AT 再発行 → `Set-Cookie: AT`
  - UI 再発行（署名なし） → `Set-Cookie: UI`
  - 応答: 204 No Content
- Browser: 以後の API を新 AT で実行

## ログアウト（端末単位）
- Browser → Backend: `POST /api/auth/logout`
- Controller:
  - 可能なら `AT` から `sid` 抽出 → `ver++`
  - `AT/UI` クリア（`Set-Cookie` Max-Age=0）
  - 応答: 204 No Content
- Browser: Cookie 削除 → 未認証へ

## アイドルタイムアウト（120分）
- Browser → Backend: 任意の `/api/**`（古い `AT`）
- `AtCookieAuthenticationFilter`:
  - `lastSeen` と現在からアイドル超過を検知
  - （refresh 以外）`ver++` ＋ `AT/UI` クリア、後段で 401
- Browser: 401 を受け `POST /api/auth/refresh` を試行
  - `ver` 不一致を検知して 401（`AT/UI` クリア）→ 再ログインへ誘導

## 補足
- コールバック受け口: `GET /login/oauth2/code/{registrationId}`（Spring Security 標準、コントローラ不要）
- 成功ハンドラ: `OAuth2LoginSuccessHandler` はログイン成功直後に実行（AT/UI 配布、Redis 登録、リダイレクト）
- IdP の RT 保管: HttpSession（Spring Session/Redis）で Spring が管理（自前の `sess:{sid}` には保存しない）
- 設定の要点:
  - `spring.session.store-type: redis`（HttpSession を Redis 化）
  - OAuth2 scope に `offline_access`（IdP RT 取得・再認可に必要／Entra）。Cognito（dev）はアプリクライアント設定で RT を許可し、scope は `openid, email, profile`。
  - CSRF: `CookieCsrfTokenRepository.withHttpOnlyFalse()` を使用（`GET /api/csrf` で UI が取得）

---

## Mermaid 図

### ログイン（成功）
```mermaid
sequenceDiagram
    participant B as Browser
    participant BE as Backend
    participant SS as Spring Security (OAuth2)
    participant IDP as IdP
    participant RS as Redis (Spring Session)
    participant RM as Redis (sess:{sid})

    B->>BE: GET /oauth2/authorization/{id}
    BE-->>B: 302 Redirect to IdP
    B->>IDP: Authenticate
    IDP-->>BE: GET /login/oauth2/code/{id}
    BE->>SS: Delegate OAuth2 login
    SS->>IDP: Token exchange
    IDP-->>SS: Tokens (AT/RT)
    SS-->>BE: Auth success
    BE->>RM: Save sess:{sid} (sid, ver, lastSeen, userId)
    BE->>RS: Save AuthorizedClient (IdP AT/RT) + sid/ver/uid/displayName
    BE-->>B: Set-Cookie AT, UI; 302 /
```

### API 呼び出し
```mermaid
sequenceDiagram
    participant B as Browser
    participant BE as Backend
    participant F as AtCookieAuthenticationFilter
    participant RM as Redis (sess:{sid})

    B->>BE: GET /api/...
    BE->>F: Filter chain
    F->>F: Parse & verify AT (JWT)
    F->>RM: Validate ver + idle; touch lastSeen
    alt OK
        F-->>BE: Set SecurityContext (authenticated)
        BE-->>B: 200 OK
    else NG (not /api/auth/refresh)
        F->>RM: ver++ (invalidate)
        F-->>B: Clear AT/UI (Set-Cookie)
        note right of B: Controller later returns 401
    end
```

### AT リフレッシュ（/api/auth/refresh）
```mermaid
sequenceDiagram
    participant B as Browser
    participant BE as Backend (Controller)
    participant S as AuthRefreshService
    participant RS as Redis (Spring Session)
    participant SS as OAuth2AuthorizedClientManager

    B->>BE: POST /api/auth/refresh (with CSRF)
    BE->>S: delegate refresh
    S->>RS: Read HttpSession (sid, ver, uid, displayName)
    S->>SS: authorize(principal, request/response)
    alt Authorized client refreshed
        S-->>B: Set-Cookie AT, UI; 204
    else Failed (no AT)
        S-->>B: Clear AT/UI; 401
    end
```

### ログアウト（端末単位）
```mermaid
sequenceDiagram
    participant B as Browser
    participant BE as Backend
    participant RM as Redis (sess:{sid})

    B->>BE: POST /api/auth/logout
    BE->>BE: Try parse sid from AT
    alt sid found
        BE->>RM: ver++
    end
    BE-->>B: Clear AT/UI; 204
```

### アイドルタイムアウト（120分）
```mermaid
sequenceDiagram
    participant B as Browser
    participant BE as Backend
    participant F as AtCookieAuthenticationFilter
    participant RM as Redis (sess:{sid})
    participant RS as Redis (Spring Session)
    participant SS as OAuth2AuthorizedClientManager

    B->>BE: /api/...
    BE->>F: Filter chain
    F->>RM: Check lastSeen vs now
    alt Idle exceeded (not /api/auth/refresh)
        F->>RM: ver++
        F-->>B: Clear AT/UI; (later 401)
        B->>BE: POST /api/auth/refresh
        BE->>RS: HttpSession exists?
        alt Yes
            BE->>SS: authorize (may use IdP RT)
            SS-->>BE: Authorized client
            BE-->>B: Set-Cookie AT, UI; 204
        else No
            BE-->>B: 401; redirect to login
        end
    else OK
        BE-->>B: 200 OK
    end
```
