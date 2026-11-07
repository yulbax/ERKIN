# ByeDPI

ByeDPI (ciadpi) paketlarni qurilmada provayder DPI ularni tanimaydigan qilib oʻzgartiradi. Server kerak emas. Qanday oʻzgartirishni strategiya — ciadpi argumentlari toʻplami belgilaydi.

![ByeDPI](../img/uz/byedpi.webp)

## Strategiyalar

- Bosish strategiyani qoʻllaydi. VPN ulangan boʻlsa, faqat ByeDPI qayta ishga tushadi.
- Foiz — joriy tarmoqdagi oxirgi tekshiruvda ochilgan saytlar ulushi: Wi-Fi yoki operatorli mobil tarmoq. Foiz boʻlmasa, strategiya bu tarmoqda hali tekshirilmagan.
- ⋮ — saytlar va tarmoqlar boʻyicha batafsil, tekshirish, tahrirlash, nomini oʻzgartirish, mahkamlash, oʻchirish.
- Uzoq bosish — oʻchirish uchun bir nechtasini tanlash.
- Mahkamlanmagan oxirgi 30 ta strategiya saqlanadi. Mahkamlanganlar va faoli oʻchirilmaydi.

## Tekshirish

Burchakdagi tugma → «Hammasini tekshirish». Har bir strategiya alohida ciadpi’da ishga tushadi, bir vaqtda beshtagacha; VPN ulanishi kerak emas. Sahifa toʻliq yuklangandagina sayt ochilgan hisoblanadi. «Hammasini tekshirish»dan soʻng roʻyxat natija boʻyicha saralanadi.

Qaysi saytlar tekshirilishini boʻlim sarlavhasidagi tishli gʻildirak — «Saytlar roʻyxatlari» belgilaydi, ByeByeDPI’dagidek. Ichki roʻyxatlar: YouTube, Google Video, Discord, Telegram, ijtimoiy tarmoqlar, Cloudflare, umumiy saytlar va Türkiye; oʻzingiznikini ham qoʻshish mumkin. Oʻzgartirilgan ichki roʻyxatni asl holiga qaytarish mumkin. Strategiyalarni tekshirish, «Strategiya tanlash» va bosh ekrandagi ByeDPI koʻrsatkichi yoqilgan roʻyxatlar boʻyicha ishlaydi.

## Strategiya tanlash

«Strategiya tanlash» ByeByeDPI’dagi tayyor strategiyalar roʻyxatini yoki har qatorda bittadan strategiya yozilgan oʻz roʻyxatingizni tekshiradi. SNI soxta paketli strategiyalarga qoʻyiladi, standart qiymati `google.com`. Mos strategiyalarni qoʻllash yoki roʻyxatga qoʻshish mumkin.

## DNS

ByeDPI trafigi uchun sayt manzillarini «Sozlamalar → Tarmoq»dagi «ByeDPI uchun DNS» aniqlaydi: standart holatda shifrlangan (DoT) 1.1.1.1.

ciadpi argumentlari: [byedpi tavsifi](https://github.com/hufrea/byedpi#readme).
