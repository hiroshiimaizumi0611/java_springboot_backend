# Backend

Java + SpringBoot 製 API。
**Docker Compose** で立ち上げられるようにしてあります。

---

## ⚡ 使い方

```bash
# ① 環境変数サンプルをコピー
cp .env.example .env

# ②  バックエンドを起動
docker compose up -d

# ③ フロントエンドリポジトリClone後にを起動
docker compose up -d # 👉 http://localhost:3000 へアクセス

# ④ サンプルユーザーでログイン
SampleUser : 
    usernmae : testuser
    password : password
