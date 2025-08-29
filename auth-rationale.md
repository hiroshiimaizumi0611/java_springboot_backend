# 認証/認可フローの比較と採用理由（flow.md vs 現行実装）

この文書は、`flow.md` で提案されている認証/認可フローと、現行コードベース（Spring Security 標準の OAuth2 Login + 自前 AT + Redis）での実装を比較し、相違点/理由/採用方針をまとめたものです。

## 1. 差分サマリ
- OAuth2 コールバック受け口: 提案は `/api/auth/callback`。現行は Spring Security 標準の `/login/oauth2/code/{registrationId}` を使用。
- IdP トークン（AT/RT）の保管先: 提案は Redis（キー=ユーザーID/sub）。現行は Spring Session（HttpSession を Redis 永続化）でフレームワーク管理。
- 自前 AT の更新方式: 提案は「認証フィルタ内で自動リフレッシュ」。現行は「`POST /api/auth/refresh` 明示エンドポイント + CSRF 必須」。
- セッション識別子/整合性: 提案はユーザーID（sub）を主キーに ver 管理。現行はデバイス単位 `sid`（UUID） + `ver` を Redis の `sess:{sid}` に保持。
- CSRF 管理: 提案は明示記載なし。現行は `XSRF-TOKEN` Cookie（HttpOnly=false, SameSite=Lax）を配布。ログイン成功時に自動発行し、`StableCookieCsrfTokenRepository` で削除の揺れを抑止。

## 2. 主な相違点の詳細

### 2.1 OAuth2 コールバックとログインハンドリング
- 提案: カスタムの `/api/auth/callback` でコード交換や後処理を実装。
- 現行: Spring Security 標準の `/login/oauth2/code/{registrationId}` を利用し、成功後の処理は `OAuth2LoginSuccessHandler` に委譲。
- 理由: 標準フローに乗ることで state/nonce の検証やトークン交換などのセキュリティ・周辺処理をフレームワークに委ね、実装・保守・脆弱性混入のリスクを低減。

### 2.2 IdP トークン（AT/RT）の扱い
- 提案: Azure 取得 RT と ver を Redis（キー=sub）に TTL 付きで保存し、アプリ側で直接参照・更新。
- 現行: Spring Security の `OAuth2AuthorizedClientManager` に委譲し、HttpSession（Spring Session/Redis）に保存。更新も同マネージャが実施。
- 理由: IdP 連携の責務をライブラリに任せることで、更新条件/失敗時の振る舞い/再認可の扱い等での実装バグや仕様差異を避ける。セッションスコープでの一元管理も容易。

### 2.3 自前 AT の更新（リフレッシュ）
- 提案: 認証フィルタで AT 期限切れ時に Redis の RT を用いて自動リフレッシュし、そのまま処理を継続。
- 現行: `POST /api/auth/refresh` で明示的に更新（CSRF ヘッダ必須）。フィルタは更新せず、失効時は 401 と Cookie クリア（refresh パス除く）。
- 理由:
  - セキュリティ: リフレッシュは state-changing と見做し、CSRF 防御を必須化したい（フィルタ内の自動更新だと CSRF 文脈外になりやすい）。
  - 責務分離: 認証フィルタは「検証とコンテキスト設定」に限定し、更新/外部通信（IdP 連携）はアプリ層の明示エンドポイントで行う。
  - 可観測性/制御: 401 を明確に返し、クライアントがハンドリング（1回だけ `/api/csrf` → `/api/auth/refresh` → リトライ）できる方が運用しやすい。
  - パフォーマンス: 期限切れ都度フィルタで IdP と通信するコスト/レイテンシや、レスポンス書き込み中の副作用を避ける。

### 2.4 セッション識別と ver
- 提案: ユーザーID（sub）を主キーに ver を持ち、更新/失効を管理。
- 現行: デバイス単位 `sid` を主キーに `ver` と `lastSeen` を保持（`sess:{sid}`）。ユーザー索引 `user:{userId}:sids` は補助用途。
- 理由: 端末単位での失効・タイムアウト制御が可能（片方の端末のみ失効などが容易）。ユーザーID単位 ver 管理だと全端末へ波及しやすく、意図せぬ巻き込みが起きる。

### 2.5 CSRF 対応
- 提案: 記載なし（暗黙）。
- 現行: `XSRF-TOKEN` 配布（`CookieCsrfTokenRepository.withHttpOnlyFalse()` を `StableCookieCsrfTokenRepository` でラップ）。ログイン成功時に自動発行し、Cookie の削除揺れを抑止。`/api/csrf` はフォールバック用。
- 理由: SPA 前提で Cookie ベース CSRF を採用し、`/api/auth/refresh` を含む state-changing リクエストに対して確実に防御を適用。

## 3. 現行実装を採用した理由（まとめ）
- 標準準拠と安全性: OAuth2 ログイン/AuthorizedClient の標準機構に寄せ、検証ロジックや更新の安全性を確保。
- 責務の明確化: 認証フィルタは検証のみ、更新は API。CSRF 対策・エラーハンドリング・監査も API に集約。
- デバイス単位の制御: `sid` ベースにより、端末別の ver/タイムアウト/失効が可能で UX と運用の柔軟性が高い。
- 運用容易性: 401/403 の意味が明瞭で、クライアントの共通ハンドラ（CSRF 再取得→refresh→リトライ）が実装しやすい。
- パフォーマンス/可用性: フィルタ内自動更新や頻繁な IdP 通信を避け、障害時の影響範囲を限定。

## 4. トレードオフと注意点
- 明示 refresh の実装/フロント連携が必要（自動更新よりクライアント実装コストは増える）。
- HttpSession（Spring Session/Redis）への依存により、セッションスコープの取り扱いを理解する必要がある。
- CSRF Cookie は期限切れ/消去の可能性があるため、クライアント側に 403 検知→`/api/csrf`→再試行のリカバリを実装する。
- `user_info` は非署名ヒントのため、信頼せずサーバ側判定には使用しない（現行もその前提）。

## 5. 今後の拡張余地
- 絶対セッション寿命の導入（例: `createdAt` を `sess:{sid}` に持ち、最大在席期間を強制）。
- `user:{userId}:sids` のクリーンアップ戦略（キー通知/定期バッチ/TTL 設計）。
- CSRF のローリング延長が必要なら、軽量フィルタで既存トークン再保存を導入（現状は Max-Age≈1日）。
- 監査ログの拡充（refresh 成否/401/403 の分類可視化）。

