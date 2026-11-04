#!/usr/bin/env python3
"""Build a browsable OSRS Wiki shop-stock index for native Companion rankings.

This file contains only Wiki-documented shop inventories and BASE shop prices.
The Android app joins these rows with live GE quotes at display time.
No exchange prices or profitability rankings are baked into the file.
"""
import argparse
import concurrent.futures
import datetime as dt
import json
import re
import sys
import time
from pathlib import Path

import requests
from bs4 import BeautifulSoup

API = "https://oldschool.runescape.wiki/api.php"
USER_AGENT = (
    "OSRS-Companion-Tool/0.7.1 "
    "(https://github.com/mandecoy/OSRS-Mobile-Companion; "
    "Wiki shop catalog for OSRS mobile app)"
)


def api_get(params, tries=4):
    for attempt in range(tries):
        try:
            response = requests.get(
                API, params=params, timeout=(15, 35),
                headers={"User-Agent": USER_AGENT, "Accept": "application/json"}
            )
            if response.status_code in (429, 500, 502, 503, 504):
                raise requests.HTTPError("Transient HTTP " + str(response.status_code))
            response.raise_for_status()
            data = response.json()
            if "error" in data:
                raise RuntimeError(str(data["error"]))
            return data
        except (requests.RequestException, ValueError) as error:
            if attempt == tries - 1:
                raise
            time.sleep(min(20, (2 ** attempt) + 0.5))
    raise RuntimeError("Wiki fetch failed")


def category_shop_titles():
    names, cmcontinue = [], None
    while True:
        args = {
            "action": "query", "format": "json", "list": "categorymembers",
            "cmtitle": "Category:Shops", "cmtype": "page", "cmlimit": "max"
        }
        if cmcontinue:
            args["cmcontinue"] = cmcontinue
        payload = api_get(args)
        names.extend(row["title"] for row in payload["query"]["categorymembers"])
        cmcontinue = payload.get("continue", {}).get("cmcontinue")
        if not cmcontinue:
            break
    return sorted(set(names), key=str.casefold)


def coin_price(value):
    # Reject shop tokens, reward points, noted items with annotations, etc.
    text = re.sub(r"\s+", " ", value or "").strip()
    match = re.fullmatch(r"([\d,]+)\s*(?:coins?|gp)?", text, re.IGNORECASE)
    return int(match.group(1).replace(",", "")) if match else None



# Only GP-priced shop stock belongs in the ranked arbitrage feed. The rendered
# Wiki price tables also appear for reward shops that charge points or tokens,
# where a bare integer is NOT a GP price.
OTHER_CURRENCIES = (
    "tokkul", "numulite", "numulites", "trading sticks", "golden nuggets",
    "mermaid's tears", "mermaid tears", "stardust", "anima-infused bark",
    "abyssal pearls", "archery tickets", "agility arena tickets",
    "agility tickets", "arena tickets", "slayer points", "reward points",
    "commendation points", "commendations", "volcanic mine points",
    "volcanic mine reward points", "golden nuggets", "marks of grace",
    "pieces of eight", "ecto-tokens", "ecto tokens", "ecto-token",
    "pest control points", "league points", "warrior guild tokens",
    "warriors' guild tokens", "warriors guild tokens", "pizazz points",
    "mermaid's tear", "mole claws", "mole skins", "mole claw", "mole skin",
    "wintertodt points", "bounty hunter points", "favour points",
    "favor points", "brimhaven tickets", "forestry bark", "reward tokens",
    "token points", "pearls", "reward vouchers", "mastering mixology points",
)

# These generic shop types are often not GP stores. Treat unverified prices
# conservatively rather than falsely showing them as profitable GP arbitrage.
SPECIAL_SHOP_TITLE = re.compile(
    r"\\b(?:rewards?|ticket exchange|ore exchange|nugget shop|"
    r"forestry shop|token shop|point shop|point store|"
    r"commendation shop|mark exchange|mark shop|currency exchange)\\b",
    re.IGNORECASE,
)


def currency_from_page(title, intro):
    summary = (title + " " + intro).lower()
    if any(word in summary for word in OTHER_CURRENCIES):
        return "other"
    if title.lower().startswith(("tzhaar-", "tzhaar ", "tzhaar'", "mairin's market")):
        return "other"
    if SPECIAL_SHOP_TITLE.search(title):
        return "unknown"
    # A standard OSRS NPC shop accepts coins unless documented otherwise.
    # The per-row price field must ALSO be a plain coin amount.
    return "coins"


def currency_from_cell(cell, default):
    labels = []
    for image in cell.select("img"):
        labels.extend((image.get("alt", ""), image.get("title", "")))
    for a in cell.select("a[href]"):
        labels.extend((a.get("title", ""), a.get("href", "")))
    for span in cell.select("[title], [data-item-name]"):
        labels.extend((span.get("title", ""), span.get("data-item-name", "")))
    marked = " ".join(labels).lower()
    if any(word in marked for word in OTHER_CURRENCIES):
        return "other"
    visible = cell.get_text(" ", strip=True)
    if any(word in visible.lower() for word in OTHER_CURRENCIES):
        return "other"
    if marked and ("coins" in marked or "gold pieces" in marked):
        # Shop-level non-GP evidence always overrides a coin-looking icon.
        return "coins" if default == "coins" else default
    if coin_price(visible) is None:
        return "unknown"
    return default


def parse_shop_html(title, html):
    soup = BeautifulSoup(html, "html.parser")
    location = ""
    for tr in soup.select("table.infobox tr, .infobox tr"):
        th = tr.find("th")
        if th and th.get_text(" ", strip=True).lower() == "location":
            location = tr.find("td").get_text(" ", strip=True) if tr.find("td") else ""
            break
    # The MediaWiki parse API often nests lead paragraphs in wrapper divs.
    # Find readable lead text rather than relying on direct-child <p>.
    intro_paragraphs = [
        re.sub(r"\s+", " ", p.get_text(" ", strip=True))
        for p in soup.select(".mw-parser-output p, p")
        if len(p.get_text(" ", strip=True)) > 30
    ][:5]
    intro = " ".join(intro_paragraphs)[:2500]
    text = intro_paragraphs[0][:300] if intro_paragraphs else ""
    shop_currency = currency_from_page(title, intro)

    stock = []
    for table in soup.select("table"):
        heading = " ".join(
            re.sub(r"\s+", " ", th.get_text(" ", strip=True)).lower()
            for th in table.select("th")[:12]
        )
        if not ("stock" in heading or "number in stock" in heading):
            continue
        if not ("price sold at" in heading or "price" in heading and "bought at" in heading):
            continue
        for tr in table.select("tr"):
            cells = tr.find_all("td", recursive=False)
            if len(cells) < 5:
                continue
            # Shops use either icon/item/stock/restock/sell/buy/[GE]
            # or item/stock/restock/sell/buy/[GE].
            icon_first = False
            if len(cells) >= 6:
                first_txt = cells[0].get_text(" ", strip=True)
                icon_first = not first_txt or (cells[0].find("img") is not None and len(first_txt) < 3)
            item_col = 1 if icon_first else 0
            if len(cells) < item_col + 5:
                continue
            item_cell = cells[item_col]
            anchor = next(
                (a for a in item_cell.find_all("a") if a.get_text(" ", strip=True)
                 and not a.get("href", "").startswith("/w/File:")), None
            )
            name = (anchor.get_text(" ", strip=True) if anchor else
                    item_cell.get_text(" ", strip=True))
            name = re.sub(r"\s+", " ", name).strip()
            if not name or name.lower() in {"item", "stock", "price"}:
                continue
            quantity = cells[item_col + 1].get_text(" ", strip=True)
            restock = cells[item_col + 2].get_text(" ", strip=True)
            purchase_cell = cells[item_col + 3]
            buyback_cell = cells[item_col + 4]
            currency = currency_from_cell(purchase_cell, shop_currency)
            sold = coin_price(purchase_cell.get_text(" ", strip=True))
            bought = coin_price(buyback_cell.get_text(" ", strip=True))
            # Prices are only meaningful as GP if the shop and that row are
            # positively classified as ordinary coin payments. Non-GP
            # amounts stay in the browsing catalog, but never rank.
            if currency != "coins":
                sold = None
                bought = None
            stock.append({"item": name, "stock": quantity[:42],
                          "restock": restock[:42], "purchase": sold,
                          "buyback": bought, "currency": currency})
    stock = list({s["item"].casefold(): s for s in stock}.values())
    return {"title": title, "location": location[:180], "notes": text,
            "currency": shop_currency, "stock": stock}


def load_shop(title):
    response = api_get({
        "action": "parse", "format": "json", "prop": "text", "page": title
    })
    html = response["parse"]["text"]["*"]
    return parse_shop_html(title, html)


def build(output, workers=3, sample=0):
    shops = category_shop_titles()
    if sample:
        shops = shops[:sample]
    print("Indexing", len(shops), "Wiki shop category pages", flush=True)
    results = {}
    errors = {}
    with concurrent.futures.ThreadPoolExecutor(max_workers=workers) as executor:
        jobs = {executor.submit(load_shop, title): title for title in shops}
        for i, job in enumerate(concurrent.futures.as_completed(jobs), 1):
            name = jobs[job]
            try:
                results[name] = job.result()
            except Exception as error:
                errors[name] = str(error)
                results[name] = {"title": name, "location": "", "notes": "",
                                 "currency": "unknown", "stock": [], "fetchFailed": True}
            if i % 40 == 0 or i == len(shops):
                n = sum(bool(s["stock"]) for s in results.values())
                print(f"{i}/{len(shops)} pages, {n} stocked, {len(errors)} errors", flush=True)

    stocked = sum(bool(shop["stock"]) for shop in results.values())
    priced = sum(row["purchase"] is not None
                 for shop in results.values() for row in shop["stock"])
    coins = sum(shop["currency"] == "coins" for shop in results.values())
    other = [(name, shop["currency"], len(shop["stock"]))
             for name, shop in results.items() if shop["currency"] != "coins"]
    print(f"Completed: stocked shops={stocked}, coin shops={coins}, "
          f"GP-priced items={priced}, errors={len(errors)}", flush=True)
    print("Excluded/unknown-currency shop pages:", sorted(other), flush=True)
    if not sample and (len(results) < 400 or stocked < 150 or priced < 450 or len(errors) > 75):
        print("Catalog verification failed; refusing to replace last good index.", file=sys.stderr)
        print("Example errors:", list(errors.items())[:8], file=sys.stderr)
        return 1
    dataset = {
        "schema": 2,
        "generatedAt": dt.datetime.now(dt.timezone.utc).isoformat(),
        "totalPages": len(shops),
        "stockedPages": stocked,
        "pricedItems": priced,
        "failedPages": len(errors),
        "shops": [results[title] for title in shops],
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(dataset, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print("Wrote", output, "bytes", output.stat().st_size, flush=True)
    return 0


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", default="docs/shop-catalog-v2.json")
    parser.add_argument("--workers", type=int, default=3)
    parser.add_argument("--sample", type=int, default=0)
    args = parser.parse_args()
    if not 1 <= args.workers <= 5:
        parser.error("--workers must be between 1 and 5")
    sys.exit(build(Path(args.output), args.workers, args.sample))


if __name__ == "__main__":
    main()
