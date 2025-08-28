## 🔐 認証・認可フロー

以下に、React SPA と Spring Boot を用いた Azure AD SSO を利用した認証・認可フローを示します。

### 認証フロー

① [React SPA] → ユーザーがアクセス（例：/home）  
　　└─ 認証されていないため、Azure AD SSOへリダイレクト

② → [Azure AD SSO画面]  
　　└─ ユーザーが認証（ID/PW or MFAなど）

③ → Azureが認証成功 → [Spring Boot: /api/auth/callback] にリダイレクト  
　　└─ 認証コードが付与される

④ → Spring Bootが以下を実行：  
　　├─ 認証コードを使って Azure にトークンリクエスト  
　　├─ Azureから アクセストークン + リフレッシュトークン を取得  
　　├─ アクセストークンの `sub` を抽出（ユーザー識別に使用）  
　　├─ `sub` を使って業務ロジックでユーザー情報を取得  
　　├─ Spring Bootが独自のJWT（アクセストークン）を発行（subおよびver、ユーザー情報含む）  
　　├─ Azureから取得したRTおよびverをそのままRedisにTTL期限付きで保存（キー: sub）  
　　├─ JWTをHttpOnly Cookieにセット（Secure, SameSiteなど）  
　　├─ JSからアクセス可能な情報（例：ユーザー名、部署名など）を 別Cookie(HttpOnly=false) にセット

⑤ → Spring Bootが [React SPA: /home] にリダイレクト  
　　└─ CookieにJWTが含まれている状態でSPAが表示される

⑥ → React SPAがAPIリクエスト（例：GET /api/user/info）を開始  
　　└─ 以降はJWT認可フローに従って処理される（期限切れ時はRedisのRTとver比較）

---

### 認可フロー

① [React SPA] → APIリクエスト（例：GET /api/user/info）

② → Spring Boot に到達 → Security Filter Chain が起動

③ → [JWT認証フィルター]  
　　├─ CookieからJWT抽出  
　　├─ JWTの署名・構造を検証 

　　├─ ✅ JWTが有効 → SecurityContext に Authentication をセット  
　　│　　　↓  
　　│　認可処理へ進む（④へ）  
　　│  
　　└─ ⏰ JWTが期限切れ → JWTのクレームから `ver` を取得  
　　　　　↓  
　　　　Redisから該当ユーザーのRT情報を取得（キー: sub）  
　　　　　↓  
　　　　Redisに保存されたRTの `ver` と JWTの `ver` を比較  
　　　　　├─ ✅ 一致 → RTの有効期限もOK  
　　　　　│　　　↓  
　　　　　│　新しい `ver` を生成（インクリメント）  
　　　　　│　RedisのRT情報の `ver` を更新  
　　　　　│　新しいJWTを発行 → Cookieにセット  
　　　　　│　　　↓  
　　　　　│　SecurityContext に Authentication を再セット  
　　　　　│　　　↓  
　　　　　│　認可処理へ進む（④へ）  
　　　　　│  
　　　　　└─ ❌ 不一致 or RT期限切れ → 認証失敗 → 401 Unauthorized

④ → [AuthorizationFilter]（FilterSecurityInterceptor）  
　　├─ SecurityContext から Authentication を取得  
　　├─ 該当APIのアクセス権限（@PreAuthorize, hasRole など）をチェック  
　　├─ ✅ 権限あり → コントローラーへ処理を渡す  
　　└─ ❌ 権限なし → 403 Forbidden を返却