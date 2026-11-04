#!/usr/bin/env python3
"""One-time development probe: emit exact Wiki magic tablet recipes as JSON."""
import requests
from bs4 import BeautifulSoup
import json, re
API="https://oldschool.runescape.wiki/api.php"
UA="OSRS-Companion-Tool/0.7.4 (https://github.com/mandecoy/OSRS-Mobile-Companion)"
html=requests.get(API,params={"action":"parse","format":"json","prop":"text","page":"Magic_tablet"},
headers={"User-Agent":UA},timeout=30).json()["parse"]["text"]["*"]
soup=BeautifulSoup(html,"html.parser")
for idx,table in enumerate(soup.select("table")):
    header=" ".join(x.get_text(" ",strip=True) for x in table.select("th")[:30])
    if "Tablet" not in header or "Materials" not in header:continue
    group={0:"Standard",2:"Arceuus",3:"Ancient",4:"Lunar"}.get(idx,"Other")
    count=0
    for row in table.select("tr"):
        cells=row.find_all("td",recursive=False)
        if len(cells)<5:continue
        level=cells[0].get_text(" ",strip=True)
        if not level.isdigit():continue
        title=cells[2].get_text(" ",strip=True)
        if not title:continue
        material=next((td for td in cells if any(
            a.get("title") in ("Soft clay","Dark essence block") for a in td.find_all("a",href=True))),None)
        if material is None:continue
        names=[a.get("title") for a in material.find_all("a",href=True) if a.get("title")]
        numbers=[int(n.replace(",","")) for n in re.findall(r"\d[\d,]*",material.get_text(" ",strip=True))]
        if len(names)!=len(numbers):
            print("TABLETERROR",json.dumps({"group":group,"title":title,"names":names,"numbers":numbers}),flush=True)
            continue
        print("TABLETJSON",json.dumps({"group":group,"name":title,"level":int(level),
            "items":[[name,qty] for name,qty in zip(names,numbers)]}),flush=True)
        count+=1
    print("TABLETSUMMARY",group,count,flush=True)
