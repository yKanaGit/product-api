# Product API

QuarkusとPostgreSQLを使った商品管理CRUD APIです。OpenShift上で動作します。

## 機能

- 商品の一覧表示
- 商品の新規登録
- 商品の編集
- 商品の削除
- Web UI（日本語対応）

## 技術スタック

- **フレームワーク**: Quarkus 3.8.3
- **データベース**: PostgreSQL 15
- **ORM**: Hibernate ORM Panache
- **API**: JAX-RS (RESTEasy Reactive)
- **コンテナ**: OpenShift / Kubernetes

## ローカル開発

### 前提条件

- Java 17
- Maven 3.8+
- PostgreSQL 15

### データベース起動

```bash
docker run -d --name postgres \
  -e POSTGRES_DB=productdb \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  postgres:15
```

### アプリケーション起動

```bash
mvn quarkus:dev
```

ブラウザで http://localhost:8080 にアクセス

## OpenShiftへのデプロイ

### 手動デプロイ

```bash
# PostgreSQLをデプロイ
oc apply -f k8s/postgres-secret.yaml
oc apply -f k8s/postgres-pvc.yaml
oc apply -f k8s/postgres-deployment.yaml
oc apply -f k8s/postgres-service.yaml

# アプリケーションをビルド＆デプロイ
oc new-app --name=product-api --strategy=docker --binary=true
oc start-build product-api --from-dir=. --follow

# デプロイメントを作成
oc delete deployment product-api
oc apply -f k8s/app-deployment.yaml
oc apply -f k8s/app-service.yaml
oc apply -f k8s/app-route.yaml

# イメージを設定
oc set image deployment/product-api product-api=product-api:latest --source=imagestreamtag
```

### CI/CDパイプライン（自動デプロイ）

OpenShift Pipelines (Tekton) を使った自動デプロイのセットアップ方法は [cicd/CICD_SETUP.md](cicd/CICD_SETUP.md) を参照してください。

## API エンドポイント

| メソッド | パス | 説明 |
|---------|------|------|
| GET | `/products` | 全商品を取得 |
| GET | `/products/{id}` | 指定IDの商品を取得 |
| POST | `/products` | 商品を新規作成 |
| PUT | `/products/{id}` | 指定IDの商品を更新 |
| DELETE | `/products/{id}` | 指定IDの商品を削除 |

### リクエスト例

```bash
# 商品作成
curl -X POST http://localhost:8080/products \
  -H "Content-Type: application/json" \
  -d '{"name":"Laptop","description":"High performance laptop","price":1299.99}'

# 全商品取得
curl http://localhost:8080/products
```

## ライセンス

MIT
