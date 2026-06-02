#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
AI新闻采集脚本
优先使用 Tavily API 搜索，失败时降级到 RSS 订阅源
"""
import os
import sys
import json
import requests
import xml.etree.ElementTree as ET
from datetime import datetime

TAVILY_API_KEY = os.environ.get('TAVILY_API_KEY', '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')
headers = {'User-Agent': 'Mozilla/5.0 (compatible; research-bot/1.0)'}

RSS_FEEDS = {
    'MIT Tech Review': 'https://www.technologyreview.com/feed/',
    'VentureBeat AI': 'https://venturebeat.com/category/ai/feed/',
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
            for item in channel.findall('item')[:8]:
                title = item.findtext('title', '')
                link = item.findtext('link', '')
                desc = item.findtext('description', '')
                pub = item.findtext('pubDate', '')
                if title:
                    articles.append({
                        'source': source_name,
                        'title': title.strip(),
                        'link': link.strip(),
                        'description': desc.strip()[:200] if desc else '',
                        'pubDate': pub.strip(),
                        'score': 0.5  # RSS源默认置信度较低
                    })
    except Exception as e:
        print(f'{source_name} parse error: {e}', file=sys.stderr)
    return articles

def fetch_ai_news():
    """获取AI新闻：优先Tavily，降级RSS"""
    today = datetime.now().strftime('%Y-%m-%d')
    print(f'采集AI新闻: {today}', file=sys.stderr)

    # 策略：用Tavily搜索3个不同角度，取最新最准的
    queries = [
        'AI artificial intelligence news today 2026',
        'OpenAI Anthropic Google DeepMind latest 2026',
        'artificial intelligence breakthrough research 2026',
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
                    'score': 0.9  # Tavily结果置信度高
                })
        # 不要太快
        import time; time.sleep(0.5)

    # 如果Tavily没有结果，用RSS降级
    if not tavily_results:
        print('[降级] Tavily失败，使用RSS源', file=sys.stderr)
        for name, url in RSS_FEEDS.items():
            articles = parse_rss_feed(url, name)
            tavily_results.extend(articles)
    else:
        # 补充RSS（用于对比）
        for name, url in RSS_FEEDS.items():
            articles = parse_rss_feed(url, name)
            for a in articles:
                a['score'] = 0.5  # RSS降级
            tavily_results.extend(articles)

    # 按score排序，去重
    seen = set()
    unique = []
    for a in tavily_results:
        if a['title'] not in seen:
            seen.add(a['title'])
            unique.append(a)
    unique.sort(key=lambda x: x.get('score', 0), reverse=True)

    return unique[:12]

def main():
    articles = fetch_ai_news()

    print(f'\n共获取 {len(articles)} 篇\n')
    for i, a in enumerate(articles[:8], 1):
        print(f'[{i}] [{a["source"]}] {a["title"][:70]}')
        print(f'    {a["link"][:80]}')
        if a.get('description'):
            print(f'    {a["description"][:100]}...')
        print()

    print('=== JSON OUTPUT ===')
    print(json.dumps({'date': datetime.now().strftime('%Y-%m-%d'), 'articles': articles[:8]}, ensure_ascii=False, indent=2))
    return articles

if __name__ == '__main__':
    main()