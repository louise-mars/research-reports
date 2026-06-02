#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
市场脉搏数据采集脚本
使用腾讯行情API采集A股/美股ETF数据

数据源：
- A股指数：腾讯行情 (qt.gtimg.cn)
- 美股ETF：腾讯行情 (qt.gtimg.cn) - 支持SOXX/SMH/AIQ/QQQ等
- 外汇：er-api.com

A股代码格式：
- sh000001 = 上证指数
- sz399001 = 深证成指
- sz399006 = 创业板指
- sh000300 = 沪深300

美股ETF代码格式：
- usSOXX = iShares半导体ETF
- usSMH = VanEck半导体ETF
- usAIQ = Global X AI ETF
- usQQQ = 纳指ETF
"""
import requests
import warnings
import json
from datetime import datetime

warnings.filterwarnings('ignore')

def get_cn_indices():
    """获取A股指数（腾讯行情）"""
    data = {}
    tickers = {
        'sh000001': '上证指数',
        'sz399001': '深证成指',
        'sz399006': '创业板指',
        'sh000300': '沪深300'
    }
    
    url_base = 'https://qt.gtimg.cn/q='
    tickers_list = list(tickers.keys())
    url = url_base + ','.join(tickers_list)
    
    try:
        r = requests.get(url, timeout=10, headers={'User-Agent': 'Mozilla/5.0'})
        lines = r.text.strip().split(';')
        
        for line in lines:
            if '~' not in line or 'none_match' in line:
                continue
            
            parts = line.split('~')
            if len(parts) < 33:
                continue
            
            # Extract ticker from line like v_sh000001="...
            code_start = line.find('v_s')
            if code_start < 0:
                continue
            
            code_raw = line[code_start+2:code_start+12].split('=')[0]
            
            for tick, name in tickers.items():
                if tick in code_raw:
                    price = float(parts[3]) if parts[3] else 0
                    prev = float(parts[4]) if parts[4] else 0
                    change_pct = float(parts[32]) if parts[32] else 0
                    data[tick] = {
                        'name': name,
                        'price': round(price, 2),
                        'prev_close': round(prev, 2),
                        'change_pct': round(change_pct, 2)
                    }
                    break
    except Exception as e:
        print(f'Error fetching CN indices: {e}')
    
    return data

def get_us_etf():
    """获取美股ETF数据（腾讯行情）"""
    data = {}
    tickers = {
        'usSOXX': 'iShares半导体ETF',
        'usSMH': 'VanEck半导体ETF',
        'usAIQ': 'Global X AI ETF',
        'usQQQ': '纳指ETF'
    }
    
    url = 'https://qt.gtimg.cn/q=' + ','.join(tickers.keys())
    
    try:
        r = requests.get(url, timeout=10, headers={'User-Agent': 'Mozilla/5.0'})
        lines = r.text.strip().split(';')
        
        for line in lines:
            if 'v_us' not in line or '~' not in line:
                continue
            
            parts = line.split('~')
            if len(parts) < 33:
                continue
            
            for tick, name in tickers.items():
                # Match by ticker symbol in code field
                code = parts[2] if len(parts) > 2 else ''
                if tick.replace('us', '') in code:
                    price = float(parts[3]) if parts[3] else 0
                    prev = float(parts[4]) if parts[4] else 0
                    change_pct = float(parts[32]) if parts[32] else 0
                    data[tick.replace('us', '')] = {
                        'name': name,
                        'price': round(price, 2),
                        'prev_close': round(prev, 2),
                        'change_pct': round(change_pct, 2)
                    }
                    break
    except Exception as e:
        print(f'Error fetching US ETF: {e}')
    
    return data

def get_assets():
    """获取关键资产（通过腾讯行情）"""
    # 黄金ETF(518880)、原油ETF(513500)、比特币、美元指数
    data = {}
    # Note: These are Chinese ETFs, may have different data format
    # BTC is special - try Binance or other
    return data

def get_forex():
    """获取美元/人民币汇率"""
    try:
        r = requests.get('https://open.er-api.com/v6/latest/USD', timeout=10)
        return round(r.json()['rates']['CNY'], 4)
    except:
        return None

def main():
    today = datetime.now().strftime('%Y-%m-%d')
    
    print(f"采集市场数据: {today}")
    
    cn = get_cn_indices()
    us_etf = get_us_etf()
    forex = get_forex()
    
    result = {
        'date': today,
        'cn_indices': cn,
        'us_etf': us_etf,
        'forex': {'USD/CNY': forex} if forex else {}
    }
    
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return result

if __name__ == '__main__':
    main()