# GitHub 任务跟踪

仓库：Xixi051108/accountingApp。

2026-10-15 用户已改为截图导入、识别分类记账和账本问答，详见 `docs/screenshot-import.md`。下方已发布 Issues 及其依赖表保留旧采集方案历史状态，不代表截图版的当前开发依赖；未发布新任务，也未关闭旧采集验证任务。实时采集实测不再阻塞截图版开发。

- 规格发布为父 issue，开发任务各有一个 issue。
- 使用 GitHub 连接器；安装且认证 gh 后也可以使用 gh CLI。
- 任务正文包含 Parent、What to build、Acceptance criteria、Blocked by。
- 当前连接器没有子任务和原生依赖操作；以正文中的父任务和阻塞 issue 链接记录关系。可用原生操作后优先使用原生关系。
- 已批准的任务拆分不重复询问。先搜索已有 issue，避免重复发布。
- 不关闭或改写父规格。完成一个任务才更新其状态。
- 采集验证必须取得小米 15 实测结果；只有构建成功不能关闭该任务。
- 已准备任务采用 ready-for-agent 标签。若平台未能应用标签，记录实际结果，不声称已经设置。
- PRs as a request surface: no。
- 不发布微信原文、真实商户金额、截图、设备标识或密钥。

## 已发布任务

2026-10-12 已确认连接器可写入 Issues。规格和任务均保持 open。

| Issue | 内容 | 开始前需完成 |
| --- | --- | --- |
| [#1](https://github.com/Xixi051108/accountingApp/issues/1) | V1 规格 | — |
| [#2](https://github.com/Xixi051108/accountingApp/issues/2) | 采集验证 APK | —；完成仍需小米 15 实测 |
| [#3](https://github.com/Xixi051108/accountingApp/issues/3) | 本地账本、待确认与基础统计 | #2 |
| [#4](https://github.com/Xixi051108/accountingApp/issues/4) | 自动入账 | #2、#3 |
| [#5](https://github.com/Xixi051108/accountingApp/issues/5) | DeepSeek Agent | #3、#4 |
| [#6](https://github.com/Xixi051108/accountingApp/issues/6) | 安装与更新验收 | #4、#5 |

五个开发任务均已应用 `ready-for-agent` 标签；该标签不代表依赖已完成。
父任务与依赖关系记录在任务正文中，未设置原生子任务或阻塞关系。
