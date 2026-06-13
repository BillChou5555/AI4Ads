"""从 JSON 文件导入广告（含 AI 摘要和标签），支持 content_hash 去重

用法：
    python -m scripts.import_ads ads.json              # 导入（skip 已有）
    python -m scripts.import_ads ads.json --clear       # 清空后导入
    python -m scripts.import_ads ads.json --overwrite   # 相同 content_hash 也更新
"""

import argparse
import asyncio
import hashlib
import json
import sys
from pathlib import Path

from sqlalchemy import delete, select

# 将 server 目录加入 path，支持从 server/ 下任意位置运行
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app.core.database import async_session_factory
from app.models.ad import Ad


def compute_hash(title: str, provider: str, ad_text: str) -> str:
    return hashlib.md5(f"{title}|{provider}|{ad_text}".encode()).hexdigest()


async def import_ads(json_path: str, clear: bool = False, overwrite: bool = False):
    with open(json_path, "r", encoding="utf-8") as f:
        ads_data = json.load(f)

    if not isinstance(ads_data, list):
        print("错误：JSON 顶层必须是数组")
        return

    print(f"读取到 {len(ads_data)} 条广告数据")

    async with async_session_factory() as db:
        if clear:
            await db.execute(delete(Ad))
            await db.commit()
            print("已清空 ads 表")

        inserted = 0
        updated = 0
        skipped = 0

        for ad_data in ads_data:
            # 校验必填字段
            for field in ("title", "provider", "ad_text", "tab"):
                if field not in ad_data:
                    print(f"  跳过：缺少必填字段 '{field}' → {ad_data.get('title', '未知')}")
                    skipped += 1
                    continue

            title = ad_data["title"]
            provider = ad_data["provider"]
            ad_text = ad_data["ad_text"]
            tab = ad_data["tab"]
            content_hash = compute_hash(title, provider, ad_text)

            # 检查是否已存在
            existing = await db.execute(
                select(Ad).where(Ad.content_hash == content_hash)
            )
            existing_ad = existing.scalar_one_or_none()

            if existing_ad and not overwrite:
                print(f"  跳过（已存在）: {title}")
                skipped += 1
                continue

            if existing_ad and overwrite:
                existing_ad.ai_summary = ad_data.get("ai_summary")
                existing_ad.ai_tags = ad_data.get("ai_tags", [])
                existing_ad.ad_images = ad_data.get("ad_images", [])
                existing_ad.ad_video_audio = ad_data.get("ad_video_audio")
                existing_ad.tab = tab
                updated += 1
                print(f"  更新: {title}")
            else:
                ad = Ad(
                    title=title,
                    provider=provider,
                    ad_text=ad_text,
                    ad_images=ad_data.get("ad_images", []),
                    ad_video_audio=ad_data.get("ad_video_audio"),
                    ai_summary=ad_data.get("ai_summary"),
                    ai_tags=ad_data.get("ai_tags", []),
                    tab=tab,
                    content_hash=content_hash,
                    status=1,
                )
                db.add(ad)
                inserted += 1
                print(f"  新增: {title}")

        await db.commit()

        print(f"\n完成：新增 {inserted}，更新 {updated}，跳过 {skipped}")

        # 统计
        result = await db.execute(
            select(Ad.tab, __import__("sqlalchemy").func.count(Ad.id))
            .where(Ad.status == 1)
            .group_by(Ad.tab)
        )
        print("各 Tab 数量：")
        for tab_name, cnt in result.fetchall():
            print(f"  {tab_name}: {cnt} 条")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="从 JSON 文件导入广告数据（含 AI 摘要和标签）")
    parser.add_argument("json_file", help="JSON 文件路径")
    parser.add_argument("--clear", action="store_true", help="清空所有旧数据后导入")
    parser.add_argument("--overwrite", action="store_true", help="覆盖已存在的广告（相同 content_hash）")
    args = parser.parse_args()
    asyncio.run(import_ads(args.json_file, args.clear, args.overwrite))
