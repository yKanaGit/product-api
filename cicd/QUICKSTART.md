# OpenShift Pipelines クイックスタートガイド

新しいOpenShift on AWS環境にCI/CDパイプラインをセットアップする手順。

## 前提条件

- ✅ GitHubリポジトリ: `https://github.com/yKanaGit/product-api.git`
- ✅ ローカルに全ファイル準備済み（`cicd/`ディレクトリ）
- 新しいOpenShift on AWS環境
- `oc` CLI インストール済み

## セットアップ手順（所要時間: 15-20分）

### 1. OpenShiftにログイン＆プロジェクト作成

```bash
# OpenShiftにログイン
oc login <your-cluster-url>

# プロジェクト作成
oc new-project product-demo
```

### 2. OpenShift Pipelines Operator インストール（クラスタ管理者）

```bash
cd /Users/ykanayam/VScode/product-api

# Operator インストール
oc apply -f cicd/operator/pipelines-subscription.yaml

# 確認（1-2分待つ）
oc get csv -n openshift-operators | grep pipelines
# 出力: openshift-pipelines-operator-rh.vX.XX.X ... Succeeded

# Pipelinesコンポーネント確認
oc get pods -n openshift-pipelines
# すべてRunningになるまで待つ
```

### 3. Tekton Triggers インストール

```bash
# Triggers有効化
oc apply -f cicd/operator/tekton-trigger.yaml

# 確認（2-3分待つ）
oc get tektontrigger
# READY: True になるまで待つ
```

### 4. パイプライン用リソースをデプロイ

```bash
# 権限リソース
oc apply -f cicd/auth/pipeline-serviceaccount.yaml
oc apply -f cicd/auth/pipeline-rolebinding.yaml

# ワークスペース用PVC
oc apply -f cicd/workspace/pipeline-pvc.yaml

# 確認
oc get sa,rolebinding,pvc -n product-demo | grep pipeline
```

### 5. Task と Pipeline をデプロイ

```bash
# Task
oc apply -f cicd/pipeline/task-build-and-push.yaml
oc apply -f cicd/pipeline/task-deploy.yaml

# Pipeline
oc apply -f cicd/pipeline/pipeline.yaml

# 確認
oc get tasks,pipeline -n product-demo
```

### 6. PostgreSQL をデプロイ

```bash
# データベース
oc apply -f k8s/postgres-secret.yaml
oc apply -f k8s/postgres-pvc.yaml
oc apply -f k8s/postgres-deployment.yaml
oc apply -f k8s/postgres-service.yaml

# アプリケーション（初回デプロイ用）
oc new-app --name=product-api --strategy=docker --binary=true
oc start-build product-api --from-dir=. --follow

# Deployment作成
oc delete deployment product-api
oc apply -f k8s/app-deployment.yaml
oc apply -f k8s/app-service.yaml
oc apply -f k8s/app-route.yaml

# イメージ設定
oc set image deployment/product-api product-api=product-api:latest --source=imagestreamtag

# 確認
oc get pods -n product-demo
```

### 7. 手動でパイプライン実行テスト

```bash
# パイプライン実行
oc create -f cicd/pipeline/pipelinerun.yaml

# 状態確認
oc get pipelinerun -n product-demo -w
```

**成功したら：** product-api Podが新しいイメージで再起動される

**失敗（affinity-assistant問題）が出たら：**

```bash
# 失敗したPipelineRunを削除
oc delete pipelinerun <pipelinerun-name> -n product-demo

# TektonConfigでaffinity-assistantを無効化
oc patch tektonconfig config --type merge -p '{"spec":{"pipeline":{"disable-affinity-assistant":true}}}'

# 再実行
oc create -f cicd/pipeline/pipelinerun.yaml
```

### 8. Tekton Triggers をデプロイ（自動実行用）

```bash
# Triggers
oc apply -f cicd/triggers/trigger-binding.yaml
oc apply -f cicd/triggers/trigger-template.yaml
oc apply -f cicd/triggers/event-listener.yaml
oc apply -f cicd/triggers/event-listener-route.yaml

# Webhook URL取得
oc get route product-api-listener -n product-demo -o jsonpath='{.spec.host}'
echo
```

### 9. GitHub Webhook設定

1. GitHub → Settings → Webhooks → Add webhook
2. Payload URL: `https://<上記のURL>`
3. Content type: `application/json`
4. Events: `Just the push event`
5. Add webhook

### 10. 動作確認

```bash
# コードを変更
echo "<!-- CI/CD Test $(date) -->" >> README.md

# GitHubにpush
git add README.md
git commit -m "Test CI/CD trigger"
git push

# 自動的にパイプラインが実行される
oc get pipelinerun -n product-demo -w
```

---

## トラブルシューティング

### Pod が Pending（affinity-assistant）

```bash
# PipelineRunを削除して再実行
oc delete pipelinerun <name> -n product-demo

# または、affinity-assistantを無効化
oc patch tektonconfig config --type merge -p '{"spec":{"pipeline":{"disable-affinity-assistant":true}}}'
```

### PVC が Pending

正常です。`WaitForFirstConsumer` モードのため、Podがマウントするまでボリュームは作成されません。

### Tekton Triggers が Installing のまま

```bash
# TektonTriggerの状態確認
oc describe tektontrigger trigger

# 数分待っても変わらない場合は、Operatorのログ確認
oc logs -n openshift-operators deployment/openshift-pipelines-operator --tail=100
```

---

## 完成後の運用

**開発フロー：**

1. ローカルでコード変更
2. `git push origin main`
3. 自動的にビルド→デプロイ
4. ブラウザで確認

**パイプライン実行履歴：**

```bash
# 実行履歴
oc get pipelinerun -n product-demo

# ログ確認
oc logs pipelinerun/<name> -n product-demo -f
```

**アプリケーションURL：**

```bash
oc get route product-api -n product-demo -o jsonpath='{.spec.host}'
```
