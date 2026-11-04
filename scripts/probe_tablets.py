#!/usr/bin/env python3
"""Diagnostic for extracting exact magic tablet ingredient icons and quantities."""
import requests
from bs4 import BeautifulSoup
import re
API="https://oldschool.runescape.wiki/api.php"
UA="OSRS-Companion-Tool/0.7.4 (https://github.com/mandecoy/OSRS-Mobile-Companion)"
html=requests.get(API,params={"action":"parse","format":"json","prop":"text","page":"Magic_tablet"},
headers={"User-Agent":UA},timeout=30).json()["parse"]["text"]["*"]
soup=BeautifulSoup(html,"html.parser")
for idx,table in enumerate(soup.select("table")):
    header=" ".join(x.get_text(" ",strip=True) for x in table.select("th")[:35])
    if "Tablet" not in header or "Materials" not in header:continue
    print("TABLE",idx,"HEADER",header[:350],flush=True)
    rows=table.select("tr")
    for row in rows[:12]:
        tds=row.find_all("td",recursive=False)
        if not tds:continue
        out=[]
        for td in tds:
            txt=td.get_text(" ",strip=True)
            images=[{"alt":img.get("alt"),"title":img.get("title")} for img in td.find_all("img")]
            links=[{"title":a.get("title"),"text":a.get_text(" ",strip=True)} for a in td.find_all("a",href=True) if a.get("title")]
            out.append({"text":txt[:60],"images":images[:8],"links":links[:8]})
        print("ROW",str(out)[:1900],flush=True)
    if idx<20 and len(rows)>2:print("HTML SAMPLE",str(rows[2])[:3600],flush=True)
