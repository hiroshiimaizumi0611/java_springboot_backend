## 🔐 認証・認可フロー

以下に、React SPA と Spring Boot を用いた Azure AD SSO を利用した認証・認可フローを示します。

### 認証フロー

① [React SPA] → ユーザーがアクセス（例：/home）  
　　└─ 認証されていないため、OAuth2開始エンドポイントへリダイレクト（例：GET /oauth2/authorization/azure）

② → [Azure AD SSO画面]  
　　└─ ユーザーが認証（ID/PW or MFAなど）

③ → Azureが認証成功 → [Spring Security: /login/oauth2/code/{registrationId}] にリダイレクト  
　　└─ 認可コードが付与される

④ → Spring Bootが以下を実行：  
　　├─ 認可コードを使って Azure にトークンリクエスト  
　　├─ Azureから アクセストークン + リフレッシュトークン を取得（Authorized Client として HttpSession に保存。ストアは Spring Session → Redis）  
　　├─ ユーザー識別子（例：preferred_username など）を抽出  
　　├─ Spring Boot が独自のJWT（アクセストークン: HS256, クレーム=sub/sid/ver/exp）を発行  
　　├─ 端末セッションメタ（sid/ver/lastSeen/userId）を Redis に保存（キー: sess:{sid}、TTLはスライディングで約14日）  
　　├─ JWTをHttpOnly Cookie `access_token` にセット（Path=/, SameSite=Lax, Secureは本番のみ）  
　　├─ JSからアクセス可能な情報（例：ユーザー名）を 別Cookie `user_info`(HttpOnly=false, Base64URL JSON) にセット  
　　├─ CSRFトークンをCookie（XSRF-TOKEN）として発行

⑤ → Spring Bootが [React SPA: /] にリダイレクト  
　　└─ CookieにJWTが含まれている状態でSPAが表示される

⑥ → React SPAがAPIリクエスト（例：GET /api/user/info）を開始  
　　└─ 以降はJWT認可フローに従って処理される（AT期限切れ時は `POST /api/auth/refresh` で再発行。CSRFヘッダ必須）

---

### 認可フロー

① [React SPA] → APIリクエスト（例：GET /api/user/info）

② → Spring Boot に到達 → Security Filter Chain が起動

③ → [JWT認証フィルター]  
　　├─ Cookieから `access_token` を抽出  
　　├─ JWTの署名・exp を検証  
　　├─ Redis上の端末セッション（sid/ver/lastSeen）と照合（ver一致・アイドル未超過なら lastSeen を更新） 

　　├─ ✅ 有効 → SecurityContext に Authentication をセット  
　　│　　　↓  
　　│　認可処理へ進む（④へ）  
　　│  
　　└─ ❌ 無効（署名/exp不正、ver不一致、アイドル超過）  
　　　　　↓  
　　　　リクエストが `/api/auth/refresh` の場合：Cookieは削除せず次段へ委譲（コントローラ側で生存確認と再発行）  
　　　　　↓  
　　　　それ以外のAPIの場合：Redisのverをインクリメント（失効）し、認証系Cookieを削除 → 401 Unauthorized

④ → [AuthorizationFilter]（FilterSecurityInterceptor）  
　　├─ SecurityContext から Authentication を取得  
　　├─ 該当APIのアクセス権限（@PreAuthorize, hasRole など）をチェック  
　　├─ ✅ 権限あり → コントローラーへ処理を渡す  
　　└─ ❌ 権限なし → 403 Forbidden を返却
