# Troubleshooting

**ByeDPI: sites don't open.** ByeDPI → Find strategy, then apply the one with the best result. If that does not help, change ByeDPI DNS in Settings → Network or turn off its encryption.

**VPN does not work.** On the home screen run Test latency and pick a server that answers. For a subscription, refresh it. If a subscription server stops answering while connected, ERKIN switches to the fastest one after three reconnects.

**No response on the home screen.** The line below it says what does not answer: the VPN server or ByeDPI. For ByeDPI, find a strategy on its page.

**An app does not use its channel.** Check Rules: a matching rule wins over the app's channel. Connections shows where the app really goes.

**Rules do not apply to an app.** An app in Bypass goes around ERKIN. Move it to Direct if rules should apply to it.

**A banking app notices the VPN.** Move it to Bypass: then it works outside the tunnel.

**Home network devices are unreachable.** Turn on Bypass LAN in Settings → Network.

**A blocked app still has network.** Blocking works only while ERKIN is connected.

**Send a report.** Logs → Share. The report has the app, core and ByeDPI logs.
