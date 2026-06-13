"""种子数据脚本：灌入样例广告数据，用于开发和测试

用法：
    python -m app.seed              # 灌入全部样例数据
    python -m app.seed --clear      # 清空旧数据后重新灌入
"""

import argparse
import asyncio
import hashlib
import uuid
from datetime import datetime

from app.config import settings
from app.core.database import async_session_factory
from app.models.ad import Ad
from sqlalchemy import delete, select


SAMPLE_ADS = [
    {
        "title": "Nike Air Max 270 春季新款跑鞋",
        "provider": "Nike官方旗舰店",
        "ad_text": "Nike Air Max 270 具备时尚风格和舒适感受，演绎未来风范。柔软泡棉中底搭载大体积 Max Air 气垫，造就独特缓震配置。舒适弹性内衬带来如袜子般的贴合感，在你大显身手时带来稳固锁定的出众脚感。",
        "ad_images": [
            "https://static.nike.com.cn/a/images/t_PDP_1728_v1/f_auto,q_auto:eco/vfmeh7xyaktfbsi7tj1l/AIR+MAX+270.png",
            "https://static.nike.com.cn/a/images/t_PDP_1728_v1/f_auto,q_auto:eco/leclg0uokv7rhbskpy4k/AIR+MAX+270.png",
            "https://static.nike.com.cn/a/images/t_PDP_1728_v1/f_auto,q_auto:eco/663bef21-6d63-4d1c-a9bc-acda32d38e5e/AIR+MAX+270.png",
        ],
        "tab": "ecommerce",
    },
    {
        "title": "Apple MacBook Air M4 轻薄笔记本",
        "provider": "Apple官方旗舰店",
        "ad_text": "全新 M4 芯片，18 小时续航， Liquid Retina 显示屏。轻薄至1.24kg，学生教育优惠立减800元，支持12期免息分期。",
        "ad_images": [
            "https://img10.360buyimg.com/pcpubliccms/s1440x1440_jfs/t1/263499/3/28432/19980/67c897caF7565d1cf/ebe9ea82e56f49bc.jpg",
            "https://img10.360buyimg.com/pcpubliccms/s1440x1440_jfs/t1/263591/13/28497/117281/67c897c9F85cd423e/da884760af1d95ee.jpg",
        ],
        "tab": "ecommerce",
    },
    {
        "title": "海底捞火锅 双人欢聚套餐",
        "provider": "海底捞",
        "ad_text": "精品肥牛+鲜虾滑+招牌捞面，双人欢聚套餐仅需188元！全城20家门店通用，周末不加价。免费美甲、擦鞋服务等你体验。",
        "ad_images": [
            "https://picsum.photos/seed/haidilao1/400/600",
        ],
        "tab": "local",
    },
    {
        "title": "贝壳找房 精装修两室一厅 首月免租",
        "provider": "贝壳找房",
        "ad_text": "地铁口步行5分钟，精装修全配，拎包入住。月租3500元，首月免租金。周边商场、学校、医院配套齐全。",
        "ad_images": [
            "https://picsum.photos/seed/house1/400/600",
            "https://picsum.photos/seed/house2/400/600",
            "https://picsum.photos/seed/house3/400/600",
            "https://picsum.photos/seed/house4/400/600",
        ],
        "tab": "local",
    },
    {
        "title": "华为 Pura 80 Pro 影像旗舰",
        "provider": "华为官方旗舰店",
        "ad_text": "XMAGE 影像系统，物理可变光圈，卫星通信。麒麟9020芯片，HarmonyOS NEXT，6000万像素超感光主摄。限时赠价值499元保护壳。",
        "ad_images": [
            "https://picsum.photos/seed/huawei1/400/600",
            "https://picsum.photos/seed/huawei2/400/600",
            "https://picsum.photos/seed/huawei3/400/600",
        ],
        "ad_video_audio": "https://storage.example.com/videos/huawei_pura.mp4",
        "tab": "ecommerce",
    },
    {
        "title": "星巴克 夏日冰咖季 买一送一",
        "provider": "星巴克中国",
        "ad_text": "星巴克夏日冰咖季来啦！冷萃、冰拿铁、星冰乐全场买一送一（每日14:00-17:00）。新会员注册即享3张满减券。",
        "ad_images": [
            "https://picsum.photos/seed/starbucks1/400/600",
        ],
        "tab": "local",
    },
    {
        "title": "优衣库 UT 系列 2026夏季新款T恤",
        "provider": "优衣库官方旗舰店",
        "ad_text": "UT系列联名限定款，100%纯棉面料，亲肤透气不变形。20+款印花可选，两件9折三件8折，满199包邮。",
        "ad_images": [
            "https://picsum.photos/seed/uniqlo1/400/600",
            "https://picsum.photos/seed/uniqlo2/400/600",
            "https://picsum.photos/seed/uniqlo3/400/600",
            "https://picsum.photos/seed/uniqlo4/400/600",
            "https://picsum.photos/seed/uniqlo5/400/600",
            "https://picsum.photos/seed/uniqlo6/400/600",
        ],
        "tab": "ecommerce",
    },
    {
        "title": "Peloton 智能动感单车 居家健身首选",
        "provider": "Peloton官方旗舰店",
        "ad_text": "22英寸旋转触控屏，直播+录播课程无限畅享。静音磁控阻力系统，心率监测，Apple Watch 互联。30天免费试用，不满意全额退款。",
        "ad_images": [
            "https://picsum.photos/seed/peloton1/400/600",
            "https://picsum.photos/seed/peloton2/400/600",
        ],
        "ad_video_audio": "https://storage.example.com/videos/peloton.mp4",
        "tab": "featured",
    },
    {
        "title": "大疆 DJI Mini 5 Pro 航拍无人机",
        "provider": "DJI大疆创新",
        "ad_text": "249g轻巧机身无需登记，4K/120fps 录制，34分钟续航，全向避障。智能跟随4.0，一键成片，小白也能拍大片。",
        "ad_images": [
            "https://picsum.photos/seed/dji1/400/600",
            "https://picsum.photos/seed/dji2/400/600",
        ],
        "tab": "featured",
    },
    {
        "title": "完美日记 动物眼影盘 十二色限定款",
        "provider": "完美日记",
        "ad_text": "十二色动物眼影盘，粉质细腻易晕染，显色持久不飞粉。哑光+珠光+闪片三质地搭配，一盘搞定日常通勤到夜晚派对。",
        "ad_images": [
            "https://picsum.photos/seed/perfect1/400/600",
            "https://picsum.photos/seed/perfect2/400/600",
            "https://picsum.photos/seed/perfect3/400/600",
            "https://picsum.photos/seed/perfect4/400/600",
        ],
        "tab": "ecommerce",
    },
    {
        "title": "美团外卖 新人首单立减15元",
        "provider": "美团",
        "ad_text": "新人专享福利，首单立减15元！全城美食30分钟送达，覆盖火锅、炸鸡、奶茶、轻食等5000+商户。天天领红包，下单更优惠。",
        "ad_images": [
            "https://picsum.photos/seed/meituan1/400/600",
        ],
        "tab": "local",
    },
    {
        "title": "戴森 V16 Detect 无线吸尘器",
        "provider": "戴森官方旗舰店",
        "ad_text": "激光探测微尘，LCD屏幕实时显示颗粒数据。240AW强劲吸力，整机HEPA过滤，60分钟续航。压电式传感器智能调节吸力。",
        "ad_images": [
            "https://picsum.photos/seed/dyson1/400/600",
            "https://picsum.photos/seed/dyson2/400/600",
            "https://picsum.photos/seed/dyson3/400/600",
        ],
        "tab": "featured",
    },
    {
        "title": "Adidas Ultraboost 5.0 跑鞋",
        "provider": "Adidas官方旗舰店",
        "ad_text": "BOOST中底科技能量反馈，Primeknit+编织鞋面360度贴合。Continental马牌橡胶大底耐磨防滑。多色可选，跑者之选。",
        "ad_images": [
            "https://picsum.photos/seed/adidas1/400/600",
            "https://picsum.photos/seed/adidas2/400/600",
        ],
        "tab": "ecommerce",
    },
    {
        "title": "Sony WH-1000XM6 无线降噪耳机",
        "provider": "Sony官方旗舰店",
        "ad_text": "全新集成处理器V2，降噪性能再升级。30小时续航，快充3分钟播放3小时。LDAC高解析度音频，自适应声音控制。",
        "ad_images": [
            "https://picsum.photos/seed/sony1/400/600",
            "https://picsum.photos/seed/sony2/400/600",
        ],
        "tab": "featured",
    },
    {
        "title": "盒马鲜生 周末生鲜特卖 低至5折",
        "provider": "盒马鲜生",
        "ad_text": "智利车厘子29.9元/斤、鲜活波士顿龙虾99元/只、挪威三文鱼买二送一。App下单30分钟极速达，门店覆盖全城。",
        "ad_images": [
            "https://picsum.photos/seed/hema1/400/600",
            "https://picsum.photos/seed/hema2/400/600",
            "https://picsum.photos/seed/hema3/400/600",
        ],
        "tab": "local",
    },
    {
        "title": "LEGO 乐高 兰博基尼 Sián 科技系列",
        "provider": "LEGO官方旗舰店",
        "ad_text": "3696颗粒，1:8比例还原兰博基尼Sián FKP 37。可动V12引擎、8速变速箱、可调尾翼。成人收藏级拼搭体验。",
        "ad_images": [
            "https://picsum.photos/seed/lego1/400/600",
            "https://picsum.photos/seed/lego2/400/600",
            "https://picsum.photos/seed/lego3/400/600",
        ],
        "tab": "featured",
    },
]

# 预置 AI 摘要和标签（模拟 AI 管线结果，无需调用 LLM API 即可测试完整链路）
SAMPLE_AI_DATA = {
    "Nike Air Max 270": {
        "summary": "Nike Air Max 270气垫跑鞋，极致缓震，七色可选限时8折",
        "tags": [
            {"category": "品类", "value": "运动鞋"},
            {"category": "品牌", "value": "Nike"},
            {"category": "风格", "value": "运动休闲"},
            {"category": "受众", "value": "运动爱好者"},
        ],
    },
    "Apple MacBook": {
        "summary": "MacBook Air M4轻薄本，18h续航，学生优惠立减800",
        "tags": [
            {"category": "品类", "value": "笔记本电脑"},
            {"category": "品牌", "value": "Apple"},
            {"category": "受众", "value": "学生党"},
            {"category": "场景", "value": "办公学习"},
        ],
    },
    "海底捞": {
        "summary": "海底捞双人火锅套餐188元，20家门店通用，周末不加价",
        "tags": [
            {"category": "品类", "value": "火锅"},
            {"category": "品牌", "value": "海底捞"},
            {"category": "场景", "value": "聚餐约会"},
            {"category": "风格", "value": "高性价比"},
        ],
    },
    "贝壳找房": {
        "summary": "地铁口精装两室一厅，首月免租3500元，拎包入住",
        "tags": [
            {"category": "品类", "value": "租房"},
            {"category": "受众", "value": "上班族"},
            {"category": "场景", "value": "地铁通勤"},
            {"category": "风格", "value": "精装修"},
        ],
    },
    "华为": {
        "summary": "华为Pura 80 Pro影像旗舰，卫星通信，赠保护壳",
        "tags": [
            {"category": "品类", "value": "智能手机"},
            {"category": "品牌", "value": "华为"},
            {"category": "受众", "value": "科技爱好者"},
            {"category": "风格", "value": "高端旗舰"},
        ],
    },
    "星巴克": {
        "summary": "星巴克夏日冰咖季，买一送一，新会员送券",
        "tags": [
            {"category": "品类", "value": "咖啡饮品"},
            {"category": "品牌", "value": "星巴克"},
            {"category": "场景", "value": "下午茶"},
            {"category": "风格", "value": "高性价比"},
        ],
    },
    "优衣库": {
        "summary": "UT系列联名T恤2026新款，纯棉透气，两件9折",
        "tags": [
            {"category": "品类", "value": "T恤"},
            {"category": "品牌", "value": "优衣库"},
            {"category": "受众", "value": "学生党"},
            {"category": "风格", "value": "简约休闲"},
        ],
    },
    "Peloton": {
        "summary": "Peloton智能动感单车，直播课程无限畅享，30天免费试用",
        "tags": [
            {"category": "品类", "value": "健身器材"},
            {"category": "品牌", "value": "Peloton"},
            {"category": "受众", "value": "健身爱好者"},
            {"category": "场景", "value": "居家健身"},
        ],
    },
    "大疆": {
        "summary": "DJI Mini 5 Pro航拍无人机，249g轻巧机身，4K录制",
        "tags": [
            {"category": "品类", "value": "无人机"},
            {"category": "品牌", "value": "DJI"},
            {"category": "受众", "value": "摄影爱好者"},
            {"category": "场景", "value": "户外旅行"},
        ],
    },
    "完美日记": {
        "summary": "完美日记十二色眼影盘，易晕染不飞粉，一盘搞定全妆",
        "tags": [
            {"category": "品类", "value": "眼影"},
            {"category": "品牌", "value": "完美日记"},
            {"category": "受众", "value": "美妆爱好者"},
            {"category": "风格", "value": "时尚潮流"},
        ],
    },
    "美团": {
        "summary": "美团外卖新人首单立减15元，全城美食30分钟送达",
        "tags": [
            {"category": "品类", "value": "外卖"},
            {"category": "品牌", "value": "美团"},
            {"category": "受众", "value": "上班族"},
            {"category": "风格", "value": "高性价比"},
        ],
    },
    "戴森": {
        "summary": "戴森V16无线吸尘器，激光探测微尘，60分钟续航",
        "tags": [
            {"category": "品类", "value": "吸尘器"},
            {"category": "品牌", "value": "戴森"},
            {"category": "受众", "value": "家庭用户"},
            {"category": "风格", "value": "高端品质"},
        ],
    },
    "Adidas": {
        "summary": "Adidas Ultraboost 5.0跑鞋，BOOST中底，能量反馈",
        "tags": [
            {"category": "品类", "value": "运动鞋"},
            {"category": "品牌", "value": "Adidas"},
            {"category": "受众", "value": "跑步爱好者"},
            {"category": "风格", "value": "运动休闲"},
        ],
    },
    "Sony": {
        "summary": "Sony WH-1000XM6降噪耳机，30h续航，LDAC高解析",
        "tags": [
            {"category": "品类", "value": "耳机"},
            {"category": "品牌", "value": "Sony"},
            {"category": "受众", "value": "音乐爱好者"},
            {"category": "风格", "value": "高端旗舰"},
        ],
    },
    "盒马": {
        "summary": "盒马周末生鲜特卖低至5折，车厘子29.9元，30分钟达",
        "tags": [
            {"category": "品类", "value": "生鲜"},
            {"category": "品牌", "value": "盒马鲜生"},
            {"category": "受众", "value": "家庭用户"},
            {"category": "风格", "value": "高性价比"},
        ],
    },
    "LEGO": {
        "summary": "乐高兰博基尼Sián，3696颗粒，1:8比例还原，成人收藏级",
        "tags": [
            {"category": "品类", "value": "积木玩具"},
            {"category": "品牌", "value": "LEGO"},
            {"category": "受众", "value": "收藏玩家"},
            {"category": "风格", "value": "高端品质"},
        ],
    },
}


def match_ai_data(title: str) -> dict | None:
    for keyword, data in SAMPLE_AI_DATA.items():
        if keyword in title:
            return data
    return None


def compute_hash(title: str, provider: str, ad_text: str) -> str:
    return hashlib.md5(f"{title}|{provider}|{ad_text}".encode()).hexdigest()


async def seed(clear: bool = False):
    async with async_session_factory() as db:
        if clear:
            await db.execute(delete(Ad))
            await db.commit()
            print("已清空 ads 表")

        count = 0
        for ad_data in SAMPLE_ADS:
            content_hash = compute_hash(ad_data["title"], ad_data["provider"], ad_data["ad_text"])

            # 检查是否已存在
            existing = await db.execute(select(Ad).where(Ad.content_hash == content_hash))
            if existing.scalar_one_or_none():
                print(f"  跳过（已存在）: {ad_data['title']}")
                continue

            ai = match_ai_data(ad_data["title"])
            ad = Ad(
                title=ad_data["title"],
                provider=ad_data["provider"],
                ad_text=ad_data["ad_text"],
                ad_images=ad_data.get("ad_images", []),
                ad_video_audio=ad_data.get("ad_video_audio"),
                tab=ad_data["tab"],
                content_hash=content_hash,
                status=1,
                ai_summary=ai["summary"] if ai else None,
                ai_tags=ai["tags"] if ai else [],
            )
            db.add(ad)
            count += 1

        await db.commit()
        print(f"已插入 {count} 条新广告")

        # 统计各 Tab 数量
        result = await db.execute(
            select(Ad.tab, __import__("sqlalchemy").func.count(Ad.id))
            .where(Ad.status == 1)
            .group_by(Ad.tab)
        )
        for tab, cnt in result.fetchall():
            print(f"  {tab}: {cnt} 条")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--clear", action="store_true", help="清空所有旧数据后重新灌入")
    args = parser.parse_args()
    asyncio.run(seed(clear=args.clear))
