# Backend

Java + SpringBoot 製 API。
**Docker Compose** で立ち上げられるようにしてあります。

---

## ⚡ 使い方

```bash
# ① 環境変数サンプルをコピーし
cp .env.example .env

# ② バックエンドリポジトリClone後にバックエンドを起動（まだの場合）
docker compose up -d

# ③ フロントエンドを起動
docker compose up -d # 👉 http://localhost:3000 へアクセス

# ④ サンプルユーザーでログイン
SampleUser : 
    usernmae : testuser
    password : password
