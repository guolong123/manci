# 全局参数
全局参数一般通过 manci 实例传入，传递形式如下
```groovy
manci = new ManCI(this, "debug", "ManCI V1", "rebuild")
manci.allStageOnComment = true
```

当前支持的参数如下：
* **parameters**: 定义流水线中的参数，类型为 List<Map<String, Object>>，支持的参数类型如下：
  * string: `[defaultValue: "ManCI V1", description: 'CI的名称，显示在状态表格中', name: 'CIName', type: 'string']`
  * choice: `[choices: ['dev', 'qa', 'prod'], defaultValue: 'dev', description: '环境', name: 'env', type: 'choice']`
  * text: `[defaultValue: 'ManCI V1', description: 'CI的名称，显示在状态表格中', name: 'CIName', type: 'text']`
  * file: `[type: 'file', description: '自定义测试用', name: 'TEST_FILE']`
  * boolean: `[defaultValue: true, description: '退出状态码(stage 测试用)', name: 'TEST_BOOLEAN', type: 'boolean']`
  * password: `[type: 'password', description: '自定义测试用', name: 'TEST_PASSWORD']`
  * credentials: `[type: 'credentials', description: '自定义测试用', name: 'TEST_CREDENTIALS', credentialType:'org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl', defaultValue: '', required: false]`
* **SSH_SECRET_KEY**: 定义 ssh 密钥，用来拉取git仓库代码。此密钥必须在 Jenkins 的 Credentials 中存在，类型为 ssh username with private key
* **GITEE_ACCESS_TOKEN_KEY**: 定义 gitee 的 access token，用来访问 gitee 相关的 api 接口，此密钥必须在 Jenkins 的 Credentials 中存在，类型为 secret text
* **failFast**: 快速失败，在并发执行 stage 时当其中某个 stage 失败，则其它 stage 都立即退出
* **allStageOnComment**: 是否每个 stage 都允许通过评论触发，不开启时，没有指定触发条件为`OnComment`的 stage 不能通过评论触发

# Stage 参数
## 触发策略
触发策略通过 stage 的 `trigger` 参数设置，支持的触发策略如下：
* OnPush: 当有代码提交时触发(与 PR 无关，当 Jenkins 配置中勾选了 "推送代码" 时，Jenkins 会自动触发构建)，通过 `fileMatches`参数控制仅在某些文件或目录变更时触发，例如: `"'^backend/.*|^frontend/.*'"`
* OnUpdate: 当 PR 更新时触发（需要勾选 "更新 Pull Requests"，更新选项中选择："Source Branch Update" 或 "Both Source And Target Branch Update"），通过 `fileMatches`参数控制仅在某些文件或目录变更时触发，例如: `"'^backend/.*|^frontend/.*'"`
* OnMerge: 当 PR 合并时触发（需要勾选 "合并 Pull Requests"），通过 `fileMatches`参数控制仅在某些文件或目录变更时触发，例如: `"'^backend/.*|^frontend/.*'"`
* OnComment: 当 PR 评论时触发（需要勾选 "评论 Pull Requests"，评论触发关键字默认为 `/run`， Jenkins 的`评论内容的正则表达式`需要填写为`/run.*`）
* OnOpen: 当 PR 打开时触发（需要勾选 "打开 Pull Requests"）
* OnClose: 当 PR 关闭时触发（需要勾选 "关闭 Pull Requests"）
* OnTestPass: 当 PR 测试通过时触发（需要勾选 "测试通过 Pull Requests"）
* OnApproved: 当 PR 被审核通过时触发（需要勾选 "审查通过 Pull Requests"）
* OnManual: 手动触发，该触发方式将在 Jenkins 手动运行 Job 使触发
* Always: 总是触发
* OnEnv: 当环境变量匹配时触发，通过 `envMatches` 参数控制触发条件，例如：`[role: "and", condition: ["BRANCH_NAME": "main"]]`
* OnBuildPass: 当前面的 stage 构建无失败时触发
* OnBuildFailure: 当前面的 stage 构建有失败时触发

使用方式示例：
```groovy

manci.stage("pr_note", [group: "group1", trigger: ["OnComment", "OnManual"], mark: "[访问地址](#)"]) {
    echo "hello world"
}

```
## stage组
stage 组通过 `group` 参数设置，当多个 stage group 一致时，这个组内的 stage 会顺序执行，当存在多个不同的 group 时，group 会并发执行(before 和 after 除外)
* before 组：将会在所有 stage 之前执行，before 组内的 stage 会顺序执行
* after 组：将会在所有 stage 之后执行，after 组内的 stage 会顺序执行
* 其他组：将会在 before 和 after 之间执行，其他组内的 stage 会并发执行

## stage fastFail
默认情况下，一个 stage 执行失败就会导致group 内的后续 stage 不会再执行，当 stage fastFail 设置为 false 时，当 stage 执行失败时，不会影响下一个 stage 的执行，只会保留错误，等到流水线执行完毕时抛出错误