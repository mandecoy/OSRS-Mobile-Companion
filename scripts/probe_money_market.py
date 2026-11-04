#!/usr/bin/env python3
"""Temporary verification: check tablet names against live OSRS GE mapping and quote data."""
import json, urllib.request
base="https://prices.runescape.wiki/api/v1/osrs"
headers={"User-Agent":"OSRS-Companion-Tool/0.7.5 (https://github.com/mandecoy/OSRS-Mobile-Companion)"}
def get(endpoint):
  req=urllib.request.Request(base+"/"+endpoint,headers=headers)
  with urllib.request.urlopen(req,timeout=45) as r:return json.loads(r.read())
mapping=get("mapping")
latest=get("latest")["data"]
hourly=get("1h")["data"]
try:daily=get("24h")["data"];print("DAILY_ENDPOINT",len(daily),flush=True)
except Exception as e:daily={};print("DAILY_ENDPOINT_ERROR",str(e),flush=True)
import re
from pathlib import Path
recipe_text=Path("app/src/main/java/com/mandecoy/osrscompanion/MagicTabletCatalog.kt").read_text()
tablets=re.findall(r'output = "([^"]+)"',recipe_text)
names=tablets+["Soft clay","Air rune","Law rune","Dark essence block","Crushed nest","Bird nest (empty)"]
normalized={r["name"].lower():r for r in mapping}
for tab in tablets:
  target=tab+" (tablet)"
  row=normalized.get(target.lower())
  if not row:
    print("TABLET_UNMATCHED",tab,flush=True)
  else:
    data=latest.get(str(row["id"]),{})
    print("TABLET_RESOLVED",tab,row["name"],row["id"],data.get("high"),data.get("low"),flush=True)

for wanted in names:
  options=[row for row in mapping if wanted.lower() in row["name"].lower()]
  print("MAPPING_MATCH",wanted,json.dumps([{"id":r["id"],"name":r["name"],"high":latest.get(str(r["id"]),{}).get("high"),"low":latest.get(str(r["id"]),{}).get("low"),"hourVol":hourly.get(str(r["id"]),{}).get("highPriceVolume",0)+hourly.get(str(r["id"]),{}).get("lowPriceVolume",0),"dailyVol":daily.get(str(r["id"]),{}).get("highPriceVolume",0)+daily.get(str(r["id"]),{}).get("lowPriceVolume",0)} for r in options][:15]),flush=True)
