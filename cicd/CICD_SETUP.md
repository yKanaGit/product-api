# OpenShift Pipelines CI/CD セットアップガイド

このガイドでは、product-api プロジェクトに OpenShift Pipelines (Tekton) を使った CI/CD パイプラインを設定し、GitHub からの push イベントで自動的にビルド・デプロイを実行する方法を説明します。

## 前提条件

- OpenShift クラスタへの管理者権限（Operator インストール用）
- `oc` CLI ツールがインストール済み
- `tkn` CLI ツール（オプション、Tekton パイプラインの管理用）
- GitHub リポジトリに product-api のソースコードが push 済み

## セットアップ手順

### 1. OpenShift Pipelines Operator のインストール（クラスタ管理者）

OpenShift Pipelines Operator をインストールします。

```bash
# Operator のインストール
oc apply -f cicd/operator/pipelines-subscription.yaml
```

インストールの確認：

```bash
# Operator の Pod が起動していることを確認
oc get pods -n openshift-operators | grep pipelines

# ClusterTask が作成されていることを確認
oc get clustertasks | grep git-clone
```

出力例：
```
NAME        DESCRIPTION                          AGE
git-clone   Clone a git repository to a workspace  5m
```

**注意**: Operator のインストールには数分かかる場合があります。すべての Pod が `Running` になるまで待ってください。

---

### 2. パイプライン実行に必要な権限リソースのデプロイ

ServiceAccount、RoleBinding、PVC を作成します。

```bash
# ServiceAccount の作成
oc apply -f cicd/auth/pipeline-serviceaccount.yaml

# RoleBinding の作成（edit 権限を付与）
oc apply -f cicd/auth/pipeline-rolebinding.yaml

# ワークスペース用 PVC の作成
oc apply -f cicd/workspace/pipeline-pvc.yaml
```

確認：

```bash
oc get serviceaccount pipeline -n product-demo
oc get rolebinding pipeline-edit -n product-demo
oc get pvc pipeline-workspace -n product-demo
```

---

### 3. Pipeline と Task のデプロイ

カスタム Task と Pipeline 定義をデプロイします。

```bash
# Task のデプロイ
oc apply -f cicd/pipeline/task-build-and-push.yaml
oc apply -f cicd/pipeline/task-deploy.yaml

# Pipeline のデプロイ
oc apply -f cicd/pipeline/pipeline.yaml
```

確認：

```bash
# Task の確認
oc get tasks -n product-demo

# Pipeline の確認
oc get pipelines -n product-demo
```

出力例：
```
NAME                   AGE
product-api-pipeline   10s
```

---

### 4. Tekton Triggers のデプロイ（GitHub Webhook 自動実行用）

EventListener、TriggerBinding、TriggerTemplate をデプロイします。

```bash
# Triggers リソースのデプロイ
oc apply -f cicd/triggers/trigger-binding.yaml
oc apply -f cicd/triggers/trigger-template.yaml
oc apply -f cicd/triggers/event-listener.yaml

# EventListener を外部公開するための Route を作成
oc apply -f cicd/triggers/event-listener-route.yaml
```

確認：

```bash
# EventListener の確認
oc get eventlistener -n product-demo

# EventListener の Service が作成されたことを確認
oc get svc -n product-demo | grep el-product-api-listener

# Route の確認
oc get route product-api-listener -n product-demo
```

---

### 5. 手動でのパイプライン実行テスト

まず手動でパイプラインを実行して、正しく動作することを確認します。

**重要**: `cicd/pipeline/pipelinerun.yaml` の `GIT_REPO_URL` を実際の GitHub リポジトリ URL に変更してください。

```yaml
# cicd/pipeline/pipelinerun.yaml を編集
params:
  - name: GIT_REPO_URL
    value: https://github.com/<your-org>/product-api.git  # ← 実際のリポジトリURLに変更
```

パイプラインを実行：

```bash
# PipelineRun を作成（generateName により自動的にユニークな名前が付与されます）
oc create -f cicd/pipeline/pipelinerun.yaml
```

または tkn CLI を使用：

```bash
tkn pipeline start product-api-pipeline \
  --param GIT_REPO_URL=https://github.com/<your-org>/product-api.git \
  --param GIT_REVISION=main \
  --workspace name=shared-workspace,claimName=pipeline-workspace \
  --serviceaccount pipeline \
  --showlog
```

進行状況の確認：

```bash
# PipelineRun の一覧を表示
tkn pipelinerun list

# ログをリアルタイムで表示
tkn pipelinerun logs -f -L

# または oc コマンドで確認
oc get pipelineruns -n product-demo
```

成功すると、product-api の Deployment が新しいイメージで更新され、Pod が再起動されます。

```bash
# Deployment の状態を確認
oc get pods -n product-demo -l app=product-api
```

---

### 6. GitHub Webhook URL の取得

EventListener の外部 URL を取得します。

```bash
# Webhook URL を取得
oc get route product-api-listener -n product-demo -o jsonpath='{.spec.host}'
echo
```

出力例：
```
product-api-listener-product-demo.apps.cluster-bqx4q.bqx4q.sandbox3558.opentlc.com
```

完全な Webhook URL は以下の形式になります：
```
https://product-api-listener-product-demo.apps.cluster-bqx4q.bqx4q.sandbox3558.opentlc.com
```

---

### 7. GitHub 側の Webhook 設定

GitHub リポジトリに Webhook を設定して、push イベントで自動的にパイプラインを実行するようにします。

#### 手順：

1. GitHub リポジトリのページを開く
2. **Settings** タブをクリック
3. 左メニューから **Webhooks** を選択
4. **Add webhook** ボタンをクリック
5. 以下の項目を設定：
   - **Payload URL**: `https://<上記で取得したURL>`
   - **Content type**: `application/json`
   - **Secret**: （空欄でOK、セキュリティを強化する場合は設定）
   - **Which events would you like to trigger this webhook?**: 
     - 「Just the push event」を選択
   - **Active**: ✓ チェックを入れる
6. **Add webhook** ボタンをクリック

#### 確認：

Webhook が正しく設定されると、GitHub の Webhook ページに緑のチェックマークが表示されます。

---

### 8. 自動パイプライン実行の確認

GitHub にコードを push して、自動的にパイプラインが実行されることを確認します。

```bash
# ローカルで変更を加える（例: README を更新）
echo "# CI/CD Test" >> README.md
git add README.md
git commit -m "Test CI/CD pipeline trigger"
git push origin main
```

パイプラインの実行状況を確認：

```bash
# PipelineRun の一覧を確認（新しい PipelineRun が自動的に作成される）
tkn pipelinerun list

# ログを確認
tkn pipelinerun logs -f -L
```

または OpenShift Web Console で確認：
1. **Pipelines** → **PipelineRuns** を開く
2. 最新の PipelineRun をクリック
3. 各 Task の実行状況とログを確認

成功すると、product-api の Deployment が自動的に更新されます。

---

## トラブルシューティング

### Operator がインストールされない

```bash
# Operator の状態を確認
oc get subscription openshift-pipelines-operator -n openshift-operators -o yaml
oc get csv -n openshift-operators | grep pipelines
```

### PipelineRun が失敗する

```bash
# PipelineRun の詳細を確認
oc describe pipelinerun <pipelinerun-name> -n product-demo

# Task の Pod のログを確認
oc logs <pod-name> -n product-demo
```

よくあるエラー：
- **Git clone エラー**: リポジトリ URL が正しいか確認
- **Build エラー**: Dockerfile の構文エラーや依存関係の問題
- **権限エラー**: ServiceAccount に適切な権限が付与されているか確認

### Webhook が動作しない

```bash
# EventListener の Pod を確認
oc get pods -n product-demo | grep el-product-api-listener
oc logs <eventlistener-pod-name> -n product-demo
```

GitHub の Webhook ページで「Recent Deliveries」を確認し、レスポンスコードとエラーメッセージを確認してください。

---

## まとめ

これで、以下の CI/CD フローが完成しました：

1. 開発者が GitHub にコードを push
2. GitHub Webhook が OpenShift の EventListener にイベントを送信
3. TriggerTemplate が PipelineRun を自動生成
4. Pipeline が実行され、以下の Task が順次実行される：
   - **fetch-repository**: Git からソースコード取得
   - **build-and-push**: buildah でコンテナイメージをビルドし、OpenShift 内部レジストリにプッシュ
   - **deploy**: Deployment を新しいイメージで更新
5. product-api Pod が新しいバージョンで再起動

パイプラインの実行履歴は OpenShift Web Console または `tkn` CLI で確認できます。

---

## 参考コマンド

```bash
# すべての PipelineRun を表示
tkn pipelinerun list

# 特定の PipelineRun のログを表示
tkn pipelinerun logs <pipelinerun-name> -f

# PipelineRun を削除
oc delete pipelinerun <pipelinerun-name> -n product-demo

# すべてのリソースを削除（クリーンアップ）
oc delete -f cicd/triggers/
oc delete -f cicd/pipeline/
oc delete -f cicd/auth/
oc delete -f cicd/workspace/
```

Tekton CLI のインストール（オプション）：
```bash
# macOS
brew install tektoncd-cli

# Linux
curl -LO https://github.com/tektoncd/cli/releases/download/v0.33.0/tkn_0.33.0_Linux_x86_64.tar.gz
tar xvzf tkn_0.33.0_Linux_x86_64.tar.gz -C /usr/local/bin/ tkn
```
