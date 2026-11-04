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
names=["Varrock teleport","Lumbridge teleport","Falador teleport","Camelot teleport","Ardougne teleport","Barrows teleport","Ghorrock teleport","Catherby teleport","Arceuus library teleport","Soft clay","Air rune","Law rune","Dark essence block","Crushed nest","Bird nest (empty)"]
for wanted in names:
  options=[row for row in mapping if wanted.lower() in row["name"].lower()]
  print("MAPPING_MATCH",wanted,json.dumps([{"id":r["id"],"name":r["name"],"high":latest.get(str(r["id"]),{}).get("high"),"low":latest.get(str(r["id"]),{}).get("low"),"hourVol":hourly.get(str(r["id"]),{}).get("highPriceVolume",0)+hourly.get(str(r["id"]),{}).get("lowPriceVolume",0),"dailyVol":daily.get(str(r["id"]),{}).get("highPriceVolume",0)+daily.get(str(r["id"]),{}).get("lowPriceVolume",0)} for r in options][:15]),flush=True)
