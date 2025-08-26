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
 - CSRF は `CookieCsrfTokenRepository.withHttpOnlyFalse()` により `XSRF-TOKEN` Cookie を配布

---

## 4. アーキテクチャ
```
[Browser]
   ↕ Cookie: AT, JSESSIONID, UI
[BFF (Spring Boot 3)]
   ↔ HttpSession（Spring Session/Redis：Authorized Client（IdP AT/RT））
   ↔ 端末セッション情報（Redis：sid/ver/lastSeen）
   ↔ IdP（Entra / Cognito）
```

---

## 5. トークン設計
### 5.1 自前 Access Token（AT）
- 形式：JWT（HS256）
- 保存：Cookie `AT`
- 有効期間：10分
- クレーム：`iss, aud, sub, iat, nbf, exp, jti, sid, ver`

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
| `AT` | 自前アクセスJWT | `HttpOnly; Secure; SameSite=Lax; Path=/; Max-Age≈10m` |
| `JSESSIONID` | サーバセッション | `HttpOnly; Secure` |
| `XSRF-TOKEN` | CSRF トークン | `Secure; SameSite=Lax; Path=/` |
| `UI` | UI 表示用ヒント（Base64URL JSON・非署名） | `Secure; SameSite=Lax; Path=/; Max-Age≈10m` |

UI ヒント例：`uid, name, avatar, loc, rolesHint`

注記：`Secure` は本番で有効（HTTPS）。`local` プロファイルでは `Secure=false` で配布。

---

## 8. エンドポイント
- **ログイン開始**：`GET /oauth2/authorization/{registrationId}`
- **コールバック**：`GET /login/oauth2/code/{registrationId}`
- **リフレッシュ**：`POST /api/auth/refresh`
  - 受信：CSRF ヘッダ
  - 処理：
    1) HttpSession から `sid/ver/uid/displayName`（と `principalName`）を取得
    2) Redis の `sess:{sid}.ver` と HttpSession の `ver` を照合（不一致なら 401・`AT/UI` クリア）
    3) IdP 生存確認（`AuthorizedClientManager.authorize(principal)`）
    4) 成功時に新 AT 発行と `UI` 更新（署名なし）
  - 応答：`204 No Content`
- **ログアウト**：`POST /api/auth/logout`
  - 処理：`ver++`、`AT/UI` 削除
  - 応答：`204 No Content`
- **UI 情報**：`GET /api/me`
- **CSRF 取得**：`GET /api/csrf`（`XSRF-TOKEN` を Cookie で返却）

---

## 9. フロー
### 9.1 ログイン
1) `/oauth2/authorization/{registrationId}` へ遷移  
2) 認証成功後、`sid, ver=1, lastSeen` を生成し Redis 保存  
3) AT を発行し Cookie 設定、`UI` を発行（署名なし）  
4) アプリへリダイレクト

### 9.2 API 呼び出し
1) Browser が `AT` を送信  
2) BFF が AT 署名・標準クレーム検証、Redis の `ver` と照合  
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
  - 120分超の最初の API で `ver++` と `AT/UI` クリア→401。直後の `/api/auth/refresh` も `ver` 不一致のため 401（再ログインを要求）。

---

## 11. エラーレスポンス
| 事象 | ステータス | サーバ動作 |
|---|---:|---|
| JWT 無効（署名/exp/iss/aud） | 401 | `AT/UI` 削除（sid 不明のため `ver++` なし） |
| ver 不一致 | 401 | `ver++`、`AT/UI` 削除 |
| 無操作 120 分超 | 401 | `ver++`、Cookie 削除 |
| `/api/auth/refresh`：`ver` 不一致 | 401 | `AT/UI` 削除（再ログインを要求） |
| `/api/auth/refresh`：IdP 生存 NG | 401 | `AT/UI` 削除 |
| `/api/auth/refresh`：成功 | 204 | 新 `AT` と `UI` を Set-Cookie |

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
