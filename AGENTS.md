# Repository Guidelines

## プロジェクト構成とモジュール
- ルート: `backend/`（Spring Boot）、`argocd/`（デプロイ）、`db/`（Oracle 初期化）、`compose.yml`、`.env.example`。
- アプリ本体: `backend/src/main/java`（機能単位でパッケージ化）、設定は `backend/src/main/resources`。
- テスト: `backend/src/test/java`。
- インフラ資産: `backend/helm-chart/`。

## ビルド・テスト・開発コマンド
- `cd backend && ./gradlew build`: コンパイル・テスト実行・JAR 作成。
- `cd backend && ./gradlew test`: JUnit 5 によるテスト実行。
- `cd backend && ./gradlew bootRun`: ローカルで API 起動（ポート `8080`）。
- `cp .env.example .env && docker compose up -d`: Oracle/Redis（必要なら backend も）を起動。バックエンドの再ビルドは `docker compose up --build backend`。

## コーディングスタイルと命名規約
- Java 21、インデントはスペース4、文字コードは UTF-8。
- パッケージ: 小文字のドット区切り。クラス: PascalCase。メソッド/フィールド: camelCase。定数: UPPER_SNAKE_CASE。
- Spring の役割名: `*Controller`、`*Service`、`*Repository`。DTO は `*Request`/`*Response`。
- 公開 API/DTO は互換性を重視。破壊的変更は PR 説明に明記。
- IDE 既定のフォーマットで可。プッシュ前に `./gradlew check` 実行を推奨。

## テストガイドライン
- 使用: Spring Boot Test、JUnit 5、Spring Security Test。
- 配置/命名: `backend/src/test/java` に配置し、`*Test.java` を接尾。
- 方針: サービス/ユーティリティは高速な単体テスト。コントローラは `@WebMvcTest` を優先。`@SpringBootTest` は必要時のみ。
- 実行: `./gradlew test`。データビルダやモックでテストを決定的に保つ。

## コミットとプルリクエスト
- コミット: 簡潔・現在形。Conventional Commits（`feat:`/`fix:`/`chore:`/`docs:`）を推奨。履歴にある `[skip ci]` は乱用しない。
- PR: 説明、関連 Issue、テスト手順（コマンド/ケース）、変更した設定（`.env` キーや compose サービス）、API 変更時は curl 例を含める。
- 小さく焦点を絞る。デバッグ時はスクリーンショット/ログ断片を添付。

## セキュリティと設定のヒント
- 機密はコミットしない。`.env` を使用し、`.env.example` を最新に保つ。
- Oracle/Redis/S3 資格情報は環境変数から供給。デプロイ前に `docker compose` で動作確認。
- 任意: `SPRING_PROFILES_ACTIVE=local` を設定し、`bootRun` でローカル上書きを利用可能。

## 認証（新仕様）
- 方式: OAuth2/OIDC ログイン（`/oauth2/authorization/{registrationId}` → `/login/oauth2/code/{registrationId}`）。自前 JWT は HS256（共有シークレット）。Cookie は `access_token` と `user_info` を使用（user_info は署名なし）。AT=10分。シークレットは `JWT_SECRET`（32バイト以上推奨）。
 - 端末セッション情報: `sid, ver, lastSeen` を Redis に保存（Spring Session）。`ver` 不一致で失効。
- 保護: Cookie ベースで `access_token` を検証（署名/exp/`ver`）。CSRF 有効。`/auth/refresh` は CSRF ヘッダ必須。SPA は同一オリジン前提。
- Cookie属性（prod=HTTPS, local=HTTP）:
  - access_token: HttpOnly; SameSite=Lax; Path=/; Secure=true(prod)/false(local)
  - user_info: 非HttpOnly; SameSite=Lax; Path=/; Max-Age≈10m; Secure=true/false（Base64URL JSON）
- エンドポイント: `POST /api/auth/refresh`（204/IdP RT により更新）、`POST /api/auth/logout`（204/ver++/Cookie 削除）、`GET /api/csrf`（CSRF 取得）。
- 主要環境変数: `SPRING_PROFILES_ACTIVE`、`spring.security.oauth2.client.*`、`JWT_SECRET`、`REDIS_*`。
- 例
  - ログイン開始: `GET /oauth2/authorization/cognito`（dev）/ `.../azure`（prod）
  - API 呼び出し: `curl -b cookiejar -c cookiejar http://localhost:8080/api/estimates`
  - リフレッシュ: `curl -X POST -b cookiejar -c cookiejar -H "X-XSRF-TOKEN: <token>" http://localhost:8080/api/auth/refresh`

移行メモ: 互換期間なし。旧 `/api/login|refresh|logout|callback` は廃止。
