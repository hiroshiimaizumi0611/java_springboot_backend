# 認証・認可仕様書（最小構成・簡素版）

## 1. 目的
React SPA と Spring Boot 3（BFF）で SSO を実現し、サーバ発行の自前 Access Token（AT）・Redis・Cookie により簡素で運用しやすい認証・認可を提供する。

---

## 2. 役者
- **Browser（SPA）**
- **BFF（Spring Boot 3）**
- **IdP**：本番＝Entra ID、開発＝Cognito
- **Redis**（Spring Session／端末セッション情報 保存）

---

## 3. 環境
- Spring Profile で切替（`prod`=Entra、`dev`=Cognito）
- OAuth2/OIDC ログイン：`/oauth2/authorization/{registrationId}` → `/login/oauth2/code/{registrationId}`
- IdP トークンの保持・更新：Spring Security OAuth2 Client（`OAuth2AuthorizedClientManager.authorize(...)`）を使用
- HttpSession は Spring Session により Redis へ保存（Authorized Client を含む）
- OAuth2 スコープに `offline_access` を付与し、IdP 側で RT 発行を許可（Entra）。開発（Cognito）は `offline_access` を要求せず、アプリクライアント設定で RT 発行を許可（scope は `openid, email, profile`）。
- CSRF：`CookieCsrfTokenRepository.withHttpOnlyFalse()` を `StableCookieCsrfTokenRepository` でラップして使用
  - `XSRF-TOKEN` を Cookie で配布（HttpOnly=false, SameSite=Lax, Path=/, Secure=prod, Max-Age≒1d）
  - ログイン成功時に自動発行し Cookie へ保存。必要に応じて `GET /api/csrf` で再取得

---

## 4. アーキテクチャ
```
[Browser]
   ↕ Cookie: access_token, JSESSIONID, user_info, XSRF-TOKEN
[BFF (Spring Boot 3)]
   ↔ HttpSession（Spring Session/Redis：Authorized Client（IdP AT/RT））
   ↔ 端末セッション情報（Redis：sid/ver/lastSeen）
   ↔ IdP（Entra / Cognito）
```

---

## 5. トークン設計
### 5.1 自前 Access Token（AT）
- 形式：JWT（HS256）
- 保存：Cookie `access_token`
- 有効期間：10分
- クレーム：`sub, exp, sid, ver`（最小構成）

#### 5.1.1 クレーム詳細（AT）
- sub: サブジェクト（ユーザー識別子）。ログイン時はメール等のユーザーID、リフレッシュ時は `uid` を優先（なければ `principalName`、最終フォールバックで `sid`）。
- exp: 失効時刻（Epoch秒）。発行時刻＋TTL（初期値10分）。
- sid: 端末セッションID（UUID）。Redis の `sess:{sid}` と対応付け。
- ver: セッションバージョン（数値, long）。Redis 側の `ver` と一致する場合のみ有効とみなす（不一致は失効）。

注記
- 署名アルゴリズムは HS256。共有シークレットは `JWT_SECRET`（32バイト以上推奨）。
- 検証は「署名/exp」を実施し、運用上は `sid/ver` を Redis と照合する。

### 5.2 サーバ側（IdPトークン）
- 保持先：HttpSession（Spring Session により Redis 永続化）
- 期限切れ時は IdP Refresh Token を用いて `authorize(...)` が自動更新

---

## 6. 端末セッション情報（Redis）
- キー：`sess:{sid}`
- 値：
  - `userId`
  - `ver`（sessionVersion）
  - `lastSeen`（最終アクティブ）
- TTL：14日
- ユーザー索引（任意）：`user:{userId}:sids`

---

## 7. Cookie
| 名称 | 内容 | 属性 |
|---|---|---|
| `access_token` | 自前アクセスJWT | `HttpOnly; Secure; SameSite=Lax; Path=/; Max-Age≈10m` |
| `JSESSIONID` | サーバセッション | `HttpOnly; Secure` |
| `XSRF-TOKEN` | CSRF トークン | `HttpOnly=false; Secure; SameSite=Lax; Path=/; Max-Age≈1d` |
| `user_info` | UI 表示用ヒント（Base64URL JSON・非署名） | `Secure; SameSite=Lax; Path=/; Max-Age≈10m` |

UI ヒント例：`uid`（将来の拡張は別途定義）

注記：`Secure` は本番で有効（HTTPS）。`local` プロファイルでは `Secure=false` で配布。

---

## 8. エンドポイント
- **ログイン開始**：`GET /oauth2/authorization/{registrationId}`
- **コールバック**：`GET /login/oauth2/code/{registrationId}`
- **リフレッシュ**：`POST /api/auth/refresh`
  - 受信：CSRF ヘッダ
  - 処理：
    1) HttpSession から `sid/ver/uid`（と `principalName`）を取得
    2) Redis の `sess:{sid}.ver` と HttpSession の `ver` を照合（不一致なら 401・`access_token/user_info` クリア）
    3) IdP 生存確認（`AuthorizedClientManager.authorize(principal)`）
    4) 成功時に新 access_token 発行と `user_info` 更新（署名なし）
  - 応答：`204 No Content`
- **ログアウト**：`POST /api/auth/logout`
  - 処理：`ver++`、`access_token/user_info` 削除
  - 応答：`204 No Content`
- **UI 情報 API**：なし（UI は `user_info` Cookie を表示に利用。安全性の判断はサーバ応答に従う）
- **CSRF 取得**：`GET /api/csrf`（`XSRF-TOKEN` を Cookie で返却。ログイン成功時にも自動発行）

---

## 9. フロー
### 9.1 ログイン
1) `/oauth2/authorization/{registrationId}` へ遷移  
2) 認証成功後、`sid, ver=1, lastSeen` を生成し Redis 保存  
3) access_token を発行し Cookie 設定、`user_info` を発行（署名なし）  
4) CSRF トークンを生成し `XSRF-TOKEN` Cookie を設定  
5) アプリへリダイレクト

### 9.2 API 呼び出し
1) Browser が `access_token` を送信  
2) BFF が access_token 署名・標準クレーム検証、Redis の `ver` と照合  
3) 認証成功後、`lastSeen` を更新

### 9.3 リフレッシュ
1) `POST /api/auth/refresh`  
2) HttpSession 参照 → Redis の `ver` と一致確認（不一致なら 401・`AT/UI` クリア）  
3) 一致時のみ IdP 生存確認（必要に応じて RT 更新）→ 新 AT 発行 → `UI` 更新  
4) `204`

### 9.4 ログアウト（端末単位）
- `/api/auth/logout` で `ver++` と Cookie 削除

---

## 10. タイムアウト・TTL
- AT：10分
- **無操作タイムアウト**：120分（`lastSeen + 120分` で判定）
  - 120分超の最初の API で `ver++` と `access_token/user_info` クリア→401。直後の `/api/auth/refresh` も `ver` 不一致のため 401（再ログインを要求）。
 - CSRF（`XSRF-TOKEN`）のTTL：≒1日（Cookie の Max-Age）。失効時は `/api/csrf` で再取得し、以降の state-changing リクエストにヘッダ付与

---

## 11. エラーレスポンス
| 事象 | ステータス | サーバ動作 |
|---|---:|---|
| JWT 無効（署名/exp） | 401 | `access_token/user_info` 削除（sid 不明のため `ver++` なし） |
| ver 不一致 | 401 | `ver++`、`access_token/user_info` 削除 |
| 無操作 120 分超 | 401 | `ver++`、Cookie 削除 |
| `/api/auth/refresh`：`ver` 不一致 | 401 | `access_token/user_info` 削除（再ログインを要求） |
| `/api/auth/refresh`：IdP 生存 NG | 401 | `access_token/user_info` 削除 |
| `/api/auth/refresh`：成功 | 204 | 新 `access_token` と `user_info` を Set-Cookie |

---

## 12. 監査ログ
- ログイン成功/失敗
- リフレッシュ成功/失敗
- `ver++` 実行（端末）
- IdP 生存確認失敗

---

## 13. 設定値（初期）
```yaml
app:
  idp.registration-id: azure|cognito
  jwt:
    at-ttl-minutes: 10
  session:
    idle-timeout-minutes: 120
```
