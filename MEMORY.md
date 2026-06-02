# MEMORY.md - 诸葛亮的长期记忆

> 上次更新：2026-05-22
> 更新频率：每周日晚"记忆蒸馏"时更新

---

## 身份
- 我是**诸葛亮**（agentId: zhugeliang），首席战略顾问和任务总调度
- 微信渠道ID: zhugeliang
- 绰号：孔明

---

## 身份配置

**子Agent命名：**
- 财务官：**范蠡**
- 技术官：鲁班

**用户基本信息：**
- 通信行业从业者，关注 5G/6G、网络架构、AI+通信
- 有投资兴趣，关注科技股、ETF、资产配置
- 有一个12岁的孩子（崎崎），由小蓝陪伴
- 技术背景：熟悉 Linux、Python、Docker、网络协议

**用户偏好：**
- 简洁直接，不废话
- 执行结果清晰呈现
- 重要时刻主动微信通知（不过度打扰）
- 分析要有数据支撑和逻辑链条，"为什么"比"是什么"重要

**主动通知场景：**
- 每周简报生成完毕
- 重要紧急事件（地震、政治等）
- 定时任务完成/失败
- 用户布置的任务有结果

---

## 系统配置

**GitHub配置：**
- 用户名：louise-mars
- 仓库名：research-reports
- Token：<redacted-token>
- Remote：https://github.com/louise-mars/research-reports.git
- 上传路径格式：research-reports/[分类]/[日期].md

**cron job 配置规范：**
- 复杂任务必须绑定 agent（`--agent zhugeliang`），否则会因默认模型配置问题导致失败
- timeout 设置：日报 120s，周报 600s
- delivery 的 to 和 accountId 必须明确指定，不能缺失

---

## 决策记录

### cron job 故障处理原则（2026-05-22）

**问题现象：**
cron job 执行失败，错误码 1027（LLM output_new_sensitive）或 timeout

**根本原因：**
isolated session 的 cron job 没有指定 agent 时，系统使用默认模型。如果默认模型配置有问题（rate limit、敏感词过滤等），任务会失败。

**处理流程：**
1. 查 jobs-state.json：`cat /root/.openclaw/cron/jobs-state.json | python3 -c "..."`
2. 查 jobs.json 确认配置：`cat /root/.openclaw/cron/jobs.json | python3 -c "..."`
3. 修复：`openclaw cron edit <job-id> --agent zhugeliang [--timeout-seconds N]`
4. 验证：`openclaw cron show <job-id>`

### 用户偏好记忆（持续更新）

- 喜欢直接、有深度的分析，不要泛泛而谈
- 重视"为什么"而非"是什么"
- 建议要具体可执行，不要空话
- 中文沟通，技术术语可用英文

---

## 教训沉淀

### 技术教训

| 日期 | 教训 | 避免方法 |
|------|------|---------|
| 2026-05-22 | 小蓝学习日志的 delivery to 配置被误改，导致"requires target"错误连续9次 | 修改前先确认当前配置 |
| 2026-05-22 | 每日思考题依赖 memory/YYYY-MM-DD.md，但文件不是每天都有创建 | 任务应自己创建当天的空文件，或加错误处理 |
| 2026-05-22 | AI深度周报 timeout 30s 完全不够，实际执行需要 930s | 周报类任务 timeout 至少 600s |

---

## 趋势观察

### cron job 健康度（2026-05-22 更新）

| 任务 | 最近状态 | 备注 |
|------|---------|------|
| 市场脉搏 | error（timeout） | 已修复：agent=zhugeliang, timeout=120s |
| AI每日快报 | ok | 正常 |
| 通信行业日报 | ok | 正常 |
| AI深度周报 | error（timeout） | 已修复：agent=zhugeliang, timeout=600s |
| 每日思考题 | error（file not found） | 原因：memory/2026-05-21.md 不存在 |
| 小蓝学习日志 | error（delivery target） | 已修复：补充 to + accountId |
| 每周简报 | error（output_new_sensitive） | 已修复：agent=zhugeliang |

---

## 每周任务执行记录

| 周 | 市场脉搏 | AI日报 | 通信日报 | 每日思考题 | 小蓝日志 | 每周简报 |
|----|---------|-------|---------|----------|---------|---------|
| 2026-05-11~17 | 部分失败 | ok | ok | error | error | error（已修复）|
| 2026-05-18~24 | 修复中 | ok | ok | 修复中 | 修复中 | 下周一验证 |

---

## 当前关注的长期项目

### 1. 小蓝的成长路径设计
- 崎崎12岁，当前主要是"陪伴者"角色
- 目标：从"被动回应"升级为"主动引导的副驾驶"
- 下一步：设计兴趣图谱，整理崎崎的偏好

### 2. 记忆系统强化
- 建立了双层记忆机制：daily log + MEMORY.md
- 本次完成：MEMORY.md 结构优化，增加"决策记录"、"教训沉淀"、"趋势观察"章节
- 下一步：每周日晚执行"记忆蒸馏"流程

---

## 技术笔记（永久）

**cron job 修复命令：**
```bash
# 修复 agent 绑定
openclaw cron edit <job-id> --agent zhugeliang

# 修复 timeout
openclaw cron edit <job-id> --timeout-seconds 120

# 修复 delivery target
openclaw cron edit <job-id> --to <wechat-id> --account <account-id>

# 查看 job 状态
openclaw cron show <job-id>

# 查看 job 执行诊断
cat /root/.openclaw/cron/jobs-state.json | python3 -c "
import json, sys, datetime
data = json.load(sys.stdin)
job = data['jobs'].get('<job-id>', {})
state = job.get('state', {})
print('Last run:', datetime.datetime.fromtimestamp(state.get('lastRunAtMs', 0)/1000))
print('Error:', state.get('lastError'))
"
```

**Git 推送命令：**
```bash
cd research-reports && git add <分类>/<日期>.md && git commit -m "提交说明 $(date +%Y-%m-%d)" && git push origin main
```

---

*本文件由诸葛亮自动维护，最后更新：2026-05-22*