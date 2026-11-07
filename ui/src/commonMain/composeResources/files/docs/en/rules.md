# Rules and connections

A rule sends matching connections to Direct, ByeDPI, VPN or Block, whatever the app's channel.

## Rules

- Rules are checked from top to bottom and the first match wins. Drag rules to change the order.
- A rule can match a domain, a domain suffix, a keyword, an IP address or subnet, a country or a downloaded rule set.
- The App field limits a rule to one app. Without it the rule applies to every app except Bypass ones.
- A VPN rule can have its own server or chain.
- Block QUIC and Bypass LAN in Settings → Network come before the rules.

## Rule sets

The download button in the Rules top bar opens Rule sets: ad and telemetry lists, country IP ranges and the Services group with ready sets for YouTube, Telegram and Discord. You can add your own set by link.

## Connections

Connections shows where apps go while ERKIN is connected: the traffic of the session per channel and the apps with their addresses. Only apps that go through ERKIN appear, so every app except Bypass ones.

- An app shows the addresses it opened, how much traffic went and by which channel or rule.
- The ⋮ menu of an address creates a rule for that address or the whole site, for this app only. The Rules tab lists the app's rules.
