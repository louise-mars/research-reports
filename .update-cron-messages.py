#!/usr/bin/env python3
import json
import sys

JOBS_FILE = '/root/.openclaw/cron/jobs.json'

# New messages for each cron job
UPDATES = {
    'bbfd5e59-fc7c-4115-9304-1a312271aafb': """请生成今日AI每日快报，包含：
1. 🤖 AI重大进展（5-10条）：AI模型发布、企业动态、技术突破
2. 🌐 国际政治（3-5条）：影响AI行业的政策、地缘政治事件
3. 📡 通信行业（3-5条）：运营商动态、技术标准、行业趋势

格式要求：
- 输出Markdown格式，包含日期、各板块、小结
- 每条新闻标注来源
- 深度分析部分选出最重要2-3条进行解读

执行步骤：
1. 使用 date +%Y-%m-%d 获取今天日期
2. 生成完整报告
3. 保存到 research-reports/ai-daily/[日期].md
4. 使用 gh api 上传到 GitHub（仓库：louise-mars/ai-transformation-report-2026，branch：main，路径：research-reports/ai-daily/[日期].md）
5. 发送微信通知到你的微信（内容为完整报告摘要，3-5条重点）""",

    '3d97c5cd-a1b2-481e-b021-79e93dea1017': """执行AI深度周报任务（诸葛亮亲自执行，不调度子Agent）：

【第一步：读取本周每日快报】
先读取 /root/.openclaw/workspace/zhugeliang/research-reports/ai-daily/ 目录下本周的所有 .md 文件（周一到周四的快报），作为本周素材基础。

【第零步：读取本周日报素材】
在开始任何搜索之前，先读取以下目录中本周已积累的 .md 文件作为素材基础：
- /root/.openclaw/workspace/zhugeliang/research-reports/market-pulse/ 本周所有 .md（市场脉搏）
- /root/.openclaw/workspace/zhugeliang/research-reports/telecom-daily/ 本周所有 .md（通信日报）
- /root/.openclaw/workspace/zhugeliang/research-reports/ai-daily/ 本周所有 .md（AI快报）

基于这些已积累的素材：
1. 标记本周最值得深挖的3-5个信号
2. 确定哪些领域需要补充搜索
3. 识别不同日报之间可能的关联线索

【第二步：补充深度搜索（信源广度）】
基于每日快报中的信号，从以下信源深度挖掘，每个类别至少5个信源：
中文信源：机器之心、量子位、36氪AI、极客公园、钛媒体AI
英文信源：The Verge AI、Wired Tech、TechCrunch、Ars Technica、MIT Technology Review

【第三步：深度分析（字数要求：5000字+）】
趋势演变深度解读、关键人物发言解读、跨领域关联分析、反共识观点、可操作建议

【输出要求】
- 格式：Word文档（.docx）
- 保存到：/root/.openclaw/workspace/zhugeliang/research-reports/ai-weekly/[日期].docx

【Git上传】
cd /root/.openclaw/workspace/zhugeliang/research-reports && git add . && git commit -m "AI深度周报 $(date +%Y-%m-%d)" && git push

【微信通知】
通过微信发送500字精华摘要""",

    'd5017619-ba0c-4192-8d77-0f49b3ddd692': """【每周简报任务】诸葛亮亲自执行，不调度子Agent

【第零步：读取本周日报素材】
读取以下目录中本周已积累的 .md 文件：
- /root/.openclaw/workspace/zhugeliang/research-reports/market-pulse/ 本周所有 .md（市场脉搏）
- /root/.openclaw/workspace/zhugeliang/research-reports/telecom-daily/ 本周所有 .md（通信日报）
- /root/.openclaw/workspace/zhugeliang/research-reports/ai-daily/ 本周所有 .md（AI快报）

【第一阶段：信息收集】
使用web搜索，覆盖中英文权威信源：
🌐 国际政治大事（5-10条）：Reuters、AP、BBC、FT
🤖 AI重大进展（5-10条）：机器之心、量子位、TechCrunch、The Verge
📡 通信行业重大进展（5-10条）：C114、Light Reading、FierceWireless

【第二阶段：深度分析】
对每类信息，选出最重要的2-3条做深度分析（趋势推演、关联分析）

【输出要求】
- 字数：至少3000字
- 格式：Word文档（.docx）
- 保存到：/root/.openclaw/workspace/zhugeliang/research-reports/weekly-briefing/[日期].docx

【Git上传】
cd /root/.openclaw/workspace/zhugeliang/research-reports && git add . && git commit -m "每周简报 $(date +%Y-%m-%d)" && git push

【微信通知】
发送到微信通知""",

    '09368914-03ec-421b-9493-f83359043dd5': """执行每周回顾（诸葛亮亲自处理，不spawn）：

【任务】汇总本周所有信息，生成个人周回顾报告。

【数据来源】
1. 本周的对话记录（通过 /memory search 检索本周关键词）
2. 本周市场脉搏：/root/.openclaw/workspace/zhugeliang/research-reports/market-pulse/ 本周 .md
3. 本周AI快报：/root/.openclaw/workspace/zhugeliang/research-reports/ai-daily/ 本周 .md
4. 本周通信日报：/root/.openclaw/workspace/zhugeliang/research-reports/telecom-daily/ 本周 .md
5. 本周小蓝日志：/root/.openclaw/workspace/zhugeliang/xiaolan-digest/ 本周 .md

【回顾结构】
# 周回顾 YYYY-MM-DD
## 本周我关注了什么
## 市场与投资
## AI与技术
## 通信行业
## 孩子的成长
## 下周关注
## 一句话总结本周

【输出】
1. 保存到：/root/.openclaw/workspace/zhugeliang/research-reports/weekly-review/[日期].md
2. 通过微信发送精简版（每个板块一句话，共6-8句）
3. 执行git上传：cd /root/.openclaw/workspace/zhugeliang/research-reports && git add . && git commit -m "周回顾 $(date +%Y-%m-%d)" && git push"""
}

def main():
    with open(JOBS_FILE, 'r') as f:
        jobs = json.load(f)
    
    for job in jobs:
        job_id = job.get('id')
        if job_id in UPDATES:
            job['payload']['message'] = UPDATES[job_id]
            print(f"Updated: {job['name']} ({job_id[:8]}...)")
    
    with open(JOBS_FILE, 'w') as f:
        json.dump(jobs, f, indent=2, ensure_ascii=False)
    
    print("Done! All cron job messages updated.")

if __name__ == '__main__':
    main()