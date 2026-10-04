#!/usr/bin/env python3
"""marssolar.com.tr ürünlerini import-cache/products.json olarak çıkarır."""

import html
import json
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "import-cache"
BASE = "https://marssolar.com.tr"
# Daha özel kategoriler önce gelir.
CATEGORIES = [
    ("14-offgrid-inverterler", "invertorler"),
    ("15-ongrid-inverterler", "invertorler"),
    ("18-hibrit-inverterler", "invertorler"),
    ("16-litium-akueler", "akuler"),
    ("17-jel-akueler", "akuler"),
    ("8-dc-dalgic", "dalgic"),
    ("3-guenes-panelleri", "paneller"),
    ("7-solar-surucu", "suruculer"),
    ("9-sarj-kontrol-cihazi", "sarj"),
    ("10-solar-lambalar", "aydinlatma"),
    ("11-solar-kablo", "kablo"),
    ("12-solar-kablo", "kablo"),
    ("13-konnektoerler", "kablo"),
    ("5-invertorler-inverter", "invertorler"),
    ("6-akueler", "akuler"),
]


def fetch(url: str) -> str:
    return subprocess.check_output(
        ["curl", "-sS", "-L", "-A", "Mozilla/5.0", url],
        text=True,
        errors="replace",
    )


def fetch_bytes(url: str) -> bytes:
    return subprocess.check_output(["curl", "-sS", "-L", "-A", "Mozilla/5.0", url])


def product_links(page: str) -> list[str]:
    return sorted(set(re.findall(r"https://marssolar\.com\.tr/urunler/\d+-[^\"\\?#]+", page)))


def strip_text(fragment: str) -> str:
    text = re.sub(r"(?is)<br\s*/?>", "\n", fragment)
    text = re.sub(r"(?is)</p>|</li>|</h\d>", "\n", text)
    text = re.sub(r"(?is)<[^>]+>", " ", text)
    text = html.unescape(text).replace("\xa0", " ")
    text = re.sub(r"[ \t]+\n", "\n", text)
    text = re.sub(r"\n{3,}", "\n\n", text)
    text = re.sub(r"[ \t]{2,}", " ", text)
    return text.strip()


def guess_category(name: str) -> str:
    folded = name.lower()
    if any(word in folded for word in ["panel", "topcon", "bifacial"]):
        return "paneller"
    if any(word in folded for word in ["dalgic", "dalgıç", "pompa"]):
        return "dalgic"
    if any(word in folded for word in ["sürücü", "surucu", "sueruecue"]):
        return "suruculer"
    if any(word in folded for word in ["akü", "aku", "akue", "lityum", "litium", "lifepo"]):
        return "akuler"
    if any(word in folded for word in ["kablo", "konnekt"]):
        return "kablo"
    if any(word in folded for word in ["lamba", "aydınlat"]):
        return "aydinlatma"
    return "invertorler"


def parse_product(url: str, category_id: str, featured: bool) -> dict:
    page = html.unescape(fetch(url))
    product_id = re.search(r"/urunler/(\d+)-", url).group(1)
    name_match = re.search(r"<h1[^>]*>(.*?)</h1>", page, re.S)
    name = strip_text(name_match.group(1) if name_match else "") or f"Ürün {product_id}"
    price_match = re.search(r'"price_amount"\s*:\s*([0-9]+(?:\.[0-9]+)?)', page)
    price = price_match.group(1) if price_match else ""
    stock_match = re.search(r'"quantity"\s*:\s*(\d+)\s*,\s*"quantity_all_versions"', page)
    stock = int(stock_match.group(1)) if stock_match else 0
    brand_match = re.search(r'manufacturer-logo"[^>]*alt="([^"]*)"', page)
    brand = html.unescape(brand_match.group(1)).strip() if brand_match else ""
    desc_match = re.search(
        r'class="product-description">(.*?)</div>\s*</div>',
        page,
        re.S,
    )
    description = strip_text(desc_match.group(1)) if desc_match else ""
    features = re.findall(
        r'class="name">\s*([^<]+)\s*</dt>\s*<dd class="value">\s*([^<]+)',
        page,
    )
    if features:
        lines = "\n".join(f"{html.unescape(k).strip()}: {html.unescape(v).strip()}" for k, v in features)
        description = (description + "\n\n" + lines).strip()
    summary = description.replace("\n", " ").strip()
    if len(summary) > 180:
        summary = summary[:177].rstrip() + "…"
    image_match = re.search(r'property="og:image" content="([^"]+)"', page)
    image_url = image_match.group(1) if image_match else ""
    image_path = ROOT / "images" / f"ps-{product_id}.jpg"
    if image_url and not image_path.exists():
        data = fetch_bytes(image_url)
        if data[:3] == b"\xff\xd8\xff" or data[:8] == b"\x89PNG\r\n\x1a\n":
            image_path.write_bytes(data)
        else:
            image_url = ""
    return {
        "id": f"ps-{product_id}",
        "name": name,
        "summary": summary,
        "description": description[:6000],
        "price": price,
        "categoryId": category_id,
        "brand": brand,
        "stock": stock,
        "featured": featured,
        "imageUrl": image_url,
    }


def main() -> None:
    (ROOT / "images").mkdir(parents=True, exist_ok=True)
    assigned: dict[str, str] = {}
    for slug, category_id in CATEGORIES:
        for page_no in (1, 2, 3):
            url = f"{BASE}/{slug}" if page_no == 1 else f"{BASE}/{slug}?page={page_no}"
            links = product_links(fetch(url))
            if not links:
                break
            for link in links:
                assigned.setdefault(link, category_id)
    home_links = product_links(fetch(BASE + "/"))
    featured = set(home_links[:8])
    sitemap = fetch(f"{BASE}/1_tr_0_sitemap.xml")
    for link in re.findall(r"https://marssolar\.com\.tr/urunler/\d+-[^\]<\s]+", sitemap):
        link = html.unescape(link)
        assigned.setdefault(link, "")
    products = []
    for link, category_id in assigned.items():
        item = parse_product(link, category_id or guess_category(link), link in featured)
        if not item["categoryId"]:
            item["categoryId"] = guess_category(item["name"])
        products.append(item)
        print(f"{item['id']} {item['categoryId']} {item['price']} {item['name']}")
    products.sort(key=lambda item: int(item["id"].split("-")[1]))
    (ROOT / "products.json").write_text(json.dumps(products, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"yazıldı: {len(products)} ürün")


if __name__ == "__main__":
    main()
