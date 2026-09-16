#!/usr/bin/env python3
import re
import json
import urllib.request
from datetime import datetime

def parse_sonol_receipt(url_or_text):
    """
    Extracts Pairzon/Sonol link from text, follows redirect to obtain doc_id,
    and queries Pairzon document API to retrieve full transaction details.
    """
    # Find link
    match = re.search(r'https?://[^\s<>"]*pairzon\.com/[^\s<>"]+', url_or_text)
    if not match:
        return None, "No Pairzon URL found in text"
    
    url = match.group(0).rstrip('.,;()[]')
    
    # 1. If it's a short URL (e.g. /1155/xxxx), follow redirect to get doc_id
    opener = urllib.request.build_opener(urllib.request.HTTPRedirectHandler)
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
    
    try:
        with opener.open(req, timeout=15) as resp:
            final_url = resp.geturl()
    except Exception as e:
        return None, f"Failed to follow URL redirect: {e}"
    
    # Extract id parameter
    id_match = re.search(r'[?&]id=([a-f0-9\-]+)', final_url)
    if not id_match:
        # maybe final_url is already the document or short url failed
        id_match = re.search(r'([a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12})', final_url)
    
    doc_id = id_match.group(1)

    # Extract partner ID (e.g. 1155 for Sonol, 1250 for Paz)
    p_match = re.search(r'pairzon\.com/(\d+)/', url)
    partner_id = p_match.group(1) if p_match else '1155'
    if not p_match:
        p_match_query = re.search(r'[?&]p=(\d+)', final_url)
        if p_match_query:
            partner_id = p_match_query.group(1)

    # 2. Query document API
    api_url = f"https://public.pairzon.com/v1.0/documents/{doc_id}?p={partner_id}"
    req_api = urllib.request.Request(api_url, headers={'User-Agent': 'Mozilla/5.0'})
    
    try:
        with opener.open(req_api, timeout=15) as resp:
            data = json.loads(resp.read().decode('utf-8'))
    except Exception as e:
        return None, f"Failed to fetch document API data: {e}"
    
    # 3. Extract details
    total = data.get('total', 0.0)
    created_date = data.get('createdDate') or data.get('uploadedDate') or ''
    dt_str = ''
    if created_date:
        try:
            dt_clean = created_date.split('.')[0].replace('T', ' ')
            dt_obj = datetime.strptime(dt_clean, '%Y-%m-%d %H:%M:%S')
            dt_str = dt_obj.strftime('%d/%m/%Y %H:%M')
            date_only = dt_obj.strftime('%d/%m/%Y')
            year_str = str(dt_obj.year)
        except Exception:
            date_only = datetime.now().strftime('%d/%m/%Y')
            year_str = str(datetime.now().year)
    else:
        date_only = datetime.now().strftime('%d/%m/%Y')
        year_str = str(datetime.now().year)
        
    store = data.get('store', {})
    if store.get('internal_id') == '661':
        station = "סונול חואג'ה בית חורון (661)"
    elif store.get('name') and store.get('name') != 'None':
        station = store.get('name')
    elif store.get('address') and store.get('address') != 'None':
        station = store.get('address')
    else:
        station = "סונול"
        
    items = data.get('items', [])
    liters = 0.0
    item_name = 'דלק 95'
    if items:
        liters = float(items[0].get('quantity', 0.0))
        item_name = items[0].get('name', item_name)
        
    ai = {item.get('key'): item.get('value') for item in data.get('additionalInfo', []) if isinstance(item, dict)}
    rate = ai.get('literUnitPrice', '')
    if not rate and liters > 0:
        rate = f"{total / liters:.2f}"
        
    pdf_url = f"https://pdf.pairzon.com/pdf/{doc_id}/{partner_id}"
    
    return {
        'doc_id': doc_id,
        'url': url,
        'pdf_url': pdf_url,
        'date': date_only,
        'year': year_str,
        'datetime': dt_str,
        'total': total,
        'liters': liters,
        'rate': rate,
        'station': station,
        'item_name': item_name
    }, None

if __name__ == '__main__':
    res, err = parse_sonol_receipt('התקבלה חשבונית חדשה מסונול לחצו לצפייה>> https://public.pairzon.com/1155/6OtdEFX5YJG7RYpzYBHDJF')
    print('Result:', res)
    print('Error:', err)
