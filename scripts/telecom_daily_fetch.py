#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
通信行业日报采集脚本
优先使用 Tavily API 搜索，失败时降级到 RSS + C114
"""
import os
import sys
import json
import requests
import xml.etree.ElementTree as ET
import re
from datetime import datetime

TAVILY_API_KEY = os.environ.get('TAVILY_API_KEY', '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')
headers = {'User-Agent': 'Mozilla/5.0 (compatible; research-bot/1.0)'}

RSS_FEEDS = {
    'Light Reading': 'https://www.lightreading.com/rss.xml',
}

def search_tavily(query, topic='news', max_results=5):
    """用 Tavily API 搜索"""
    try:
        url = 'https://api.tavily.com/search'
        params = {
            'api_key': TAVILY_API_KEY,
            'query': query,
            'topic': topic,
            'max_results': max_results,
            'include_answer': False,
            'include_raw_content': False,
        }
        r = requests.post(url, json=params, timeout=15, headers={'Content-Type': 'application/json'})
        if r.status_code == 200:
            data = r.json()
            results = data.get('results', [])
            print(f'[Tavily] {query}: {len(results)} results', file=sys.stderr)
            return results
        else:
            print(f'[Tavily] error {r.status_code}: {r.text[:200]}', file=sys.stderr)
            return None
    except Exception as e:
        print(f'[Tavily] exception: {e}', file=sys.stderr)
        return None

def parse_rss_feed(url, source_name):
    """解析RSS feed"""
    articles = []
    try:
        r = requests.get(url, headers=headers, timeout=15)
        if r.status_code != 200:
            return articles
        root = ET.fromstring(r.content)
        channel = root.find('channel')
        if channel is not None:
            for item in channel.findall('item')[:10]:
                title = item.findtext('title', '')
                link = item.findtext('link', '')
                desc = item.findtext('description', '')
                pub = item.findtext('pubDate', '')
                if title:
                    articles.append({
                        'source': source_name,
                        'title': title.strip(),
                        'link': link.strip(),
                        'description': desc.strip()[:300] if desc else '',
                        'pubDate': pub.strip(),
                        'score': 0.5
                    })
    except Exception as e:
        print(f'{source_name} parse error: {e}', file=sys.stderr)
    return articles

def fetch_c114():
    """抓取C114通信网首页"""
    articles = []
    try:
        r = requests.get('https://www.c114.com.cn', headers=headers, timeout=15)
        if r.status_code != 200:
            return articles
        r.encoding = 'gb2312'
        text = r.text
        keywords = ['5G', '6G', '华为', '中兴', '运营商', '爱立信', '诺基亚', '通信', '网络', '光纤', '宽带', '芯片']
        pattern = re.compile(r'<a[^>]+href=["\']([^"\']+)["\'][^>]*>([^<]*(?:' + '|'.join(keywords) + ')[^<]*)</a>')
        for m in pattern.finditer(text):
            link = m.group(1)
            title = m.group(2).strip()
            if title and len(title) > 10:
                if not link.startswith('http'):
                    link = 'https://www.c114.com.cn' + link
                articles.append({'source': 'C114', 'title': title[:80], 'link': link, 'description': '', 'pubDate': '', 'score': 0.6})
        seen = set()
        unique = []
        for a in articles:
            if a['title'] not in seen:
                seen.add(a['title'])
                unique.append(a)
        return unique[:6]
    except Exception as e:
        print(f'C114 error: {e}', file=sys.stderr)
        return []

def fetch_telecom_news():
    """获取通信行业新闻：优先Tavily，降级RSS+C114"""
    today = datetime.now().strftime('%Y-%m-%d')
    print(f'采集通信日报: {today}', file=sys.stderr)

    # Tavily搜索通信相关
    queries = [
        'telecom 5G 6G network operator news today 2026',
        'Huawei Ericsson Nokia telecommunications latest 2026',
        '中国运营商 5G 通信 华为 中兴 2026',
    ]

    tavily_results = []
    for q in queries:
        results = search_tavily(q, topic='news', max_results=3)
        if results:
            for r in results:
                tavily_results.append({
                    'source': 'Tavily',
                    'title': r.get('title', ''),
                    'link': r.get('url', ''),
                    'description': r.get('content', '')[:200],
                    'pubDate': r.get('published_date', ''),
                    'score': 0.9
                })
        import time; time.sleep(0.5)

    # 降级方案
    if not tavily_results:
        print('[降级] Tavily失败，使用RSS+C114', file=sys.stderr)
        for name, url in RSS_FEEDS.items():
            articles = parse_rss_feed(url, name)
            tavily_results.extend(articles)
        tavily_results.extend(fetch_c114())
    else:
        # 补充RSS+C114
        for name, url in RSS_FEEDS.items():
            articles = parse_rss_feed(url, name)
            for a in articles:
                a['score'] = 0.5
            tavily_results.extend(articles)
        for a in fetch_c114():
            a['score'] = 0.4
            tavily_results.append(a)

    # 去重+排序
    seen = set()
    unique = []
    for a in tavily_results:
        if a['title'] not in seen:
            seen.add(a['title'])
            unique.append(a)
    unique.sort(key=lambda x: x.get('score', 0), reverse=True)

    return unique[:12]

def main():
    articles = fetch_telecom_news()

    print(f'\n共获取 {len(articles)} 篇\n')
    for i, a in enumerate(articles[:8], 1):
        print(f'[{i}] [{a["source"]}] {a["title"][:65]}')
        print(f'    {a["link"][:80]}')
        print()

    print('=== JSON OUTPUT ===')
    print(json.dumps({'date': datetime.now().strftime('%Y-%m-%d'), 'articles': articles[:8]}, ensure_ascii=False, indent=2))
    return articles

if __name__ == '__main__':
    main()