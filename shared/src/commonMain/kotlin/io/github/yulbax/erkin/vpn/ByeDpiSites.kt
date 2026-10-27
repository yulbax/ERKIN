package io.github.yulbax.erkin.vpn

import io.github.yulbax.erkin.vpn.core.ByeDpiReachability

data class SiteGroup(val name: String, val sites: List<String>)

data class SiteResult(val host: String, val group: String, val reachable: Boolean)

fun List<SiteResult>.reachability(): ByeDpiReachability = ByeDpiReachability(count { it.reachable }, size)

object ByeDpiSites {
    private val YOUTUBE = SiteGroup("YouTube", listOf(
        "youtu.be", "youtube.com", "i.ytimg.com", "i9.ytimg.com", "yt3.ggpht.com",
        "yt4.ggpht.com", "googleapis.com", "jnn-pa.googleapis.com", "googleusercontent.com",
        "signaler-pa.youtube.com", "youtubei.googleapis.com", "manifest.googlevideo.com",
        "yt3.googleusercontent.com"
    ))

    private val GOOGLE_VIDEO = SiteGroup("Google Video", listOf(
        "rr1---sn-4axm-n8vs.googlevideo.com", "rr1---sn-gvnuxaxjvh-o8ge.googlevideo.com",
        "rr1---sn-ug5onuxaxjvh-p3ul.googlevideo.com", "rr1---sn-ug5onuxaxjvh-n8v6.googlevideo.com",
        "rr4---sn-q4flrnsl.googlevideo.com", "rr10---sn-gvnuxaxjvh-304z.googlevideo.com",
        "rr14---sn-n8v7kn7r.googlevideo.com", "rr16---sn-axq7sn76.googlevideo.com",
        "rr1---sn-8ph2xajvh-5xge.googlevideo.com", "rr1---sn-gvnuxaxjvh-5gie.googlevideo.com",
        "rr12---sn-gvnuxaxjvh-bvwz.googlevideo.com", "rr5---sn-n8v7knez.googlevideo.com",
        "rr1---sn-u5uuxaxjvhg0-ocje.googlevideo.com", "rr2---sn-q4fl6ndl.googlevideo.com",
        "rr5---sn-gvnuxaxjvh-n8vk.googlevideo.com", "rr4---sn-jvhnu5g-c35d.googlevideo.com",
        "rr1---sn-q4fl6n6y.googlevideo.com", "rr2---sn-hgn7ynek.googlevideo.com",
        "rr1---sn-xguxaxjvh-gufl.googlevideo.com"
    ))

    val GROUPS: List<SiteGroup> = listOf(
        YOUTUBE,
        GOOGLE_VIDEO,
        SiteGroup("Discord", listOf(
            "dis.gd", "discord.co", "discord.gg", "discord.app", "discord.com", "discord.dev",
            "discord.new", "discord.gift", "discord.gifts", "discord.media", "discord.store",
            "discord.design", "discordapp.com", "discordcdn.com", "discordsez.com",
            "discordsays.com", "discordmerch.com", "discordpartygames.com", "discordactivities.com",
            "stable.dl2.discordapp.net",
            "discord-attachments-uploads-prd.storage.googleapis.com"
        )),
        SiteGroup("Telegram", listOf(
            "telegram.org", "core.telegram.org", "web.telegram.org", "webk.telegram.org",
            "my.telegram.org", "translations.telegram.org", "instantview.telegram.org",
            "blog.telegram.org", "comments.telegram.org", "verify.telegram.org",
            "login.telegram.org", "auth.telegram.org", "api.telegram.org", "promo.telegram.org",
            "desktop.telegram.org", "macos.telegram.org", "ios.telegram.org",
            "android.telegram.org", "reactions.telegram.org", "claims.telegram.org",
            "x.telegram.org", "help.telegram.org", "docs.telegram.org", "schema.telegram.org",
            "dev.telegram.org", "contest.telegram.org", "premium.telegram.org",
            "settings.telegram.org", "qr.telegram.org", "stickers.telegram.org",
            "emoji.telegram.org", "themes.telegram.org", "donate.telegram.org",
            "fragment.telegram.org", "ton.telegram.org", "wallet.telegram.org", "pay.telegram.org",
            "telegram.me", "telegram.dog", "telegra.ph", "telesco.pe", "web.telegram.me",
            "zws1.web.telegram.org", "zws2.web.telegram.org", "zws1.web.telegram.me",
            "zws2.web.telegram.me", "venus.web.telegram.org", "pluto.web.telegram.org",
            "aurora.web.telegram.org", "vesta.web.telegram.org", "voice.telegram.org",
            "cdn.telegram.org"
        )),
        SiteGroup("Social", listOf(
            "snapchat.com", "snap.com", "linkedin.com", "facebook.com", "fb.com", "fb.me",
            "fbcdn.net", "messenger.com", "meta.com", "instagram.com", "static.cdninstagram.com",
            "proton.me", "medium.com", "x.com", "twitter.com", "soundcloud.com"
        )),
        SiteGroup("Cloudflare", listOf(
            "cloudflare.net", "cloudflare.com", "cloudflarecn.net", "cloudflare-ech.com"
        )),
        SiteGroup("General", listOf(
            "rutracker.org", "nyaa.si", "rutor.org", "nnmclub.to", "speedtest.net", "ookla.com"
        )),
        SiteGroup("Türkiye", listOf(
            "roblox.com", "wattpad.com", "pastebin.com", "4shared.com", "wikileaks.org"
        ))
    )

    val QUICK: List<SiteGroup> = listOf(YOUTUBE, GOOGLE_VIDEO)

    val QUICK_HOSTS: Set<String> = QUICK.flatMapTo(HashSet()) { it.sites }

    val HEALTH_CHECK: List<String> = listOf(
        "youtube.com", "discord.com", "instagram.com", "facebook.com", "x.com", "twitter.com",
        "telegram.org", "rutracker.org", "proton.me", "linkedin.com", "medium.com", "cloudflare.com"
    )

    val fullTotal: Int get() = GROUPS.sumOf { it.sites.size }
}
