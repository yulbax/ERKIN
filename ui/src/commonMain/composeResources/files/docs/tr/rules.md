# Kurallar ve bağlantılar

Kural, eşleşen bağlantıları uygulamanın kanalından bağımsız olarak Direct, ByeDPI, VPN veya Block’a gönderir.

## Kurallar

- Kurallar yukarıdan aşağıya denetlenir ve ilk eşleşen geçerli olur. Sırayı sürükleyerek değiştirin.
- Kural bir alan adını, alan adı sonekini, anahtar kelimeyi, IP adresini veya alt ağı, ülkeyi ya da indirilmiş bir kural setini eşleyebilir.
- Uygulama alanı kuralı tek bir uygulamayla sınırlar. Bu alan olmadan kural Bypass’takiler dışındaki tüm uygulamalara uygulanır.
- VPN kuralının kendi sunucusu veya zinciri olabilir.
- Ayarlar → Ağ’daki QUIC’i engelle ve Yerel ağı atla kurallardan önce gelir.

## Kural setleri

Kurallar başlık çubuğundaki indirme düğmesi Kural setleri’ni açar: reklam ve telemetri listeleri, ülke IP aralıkları ve YouTube, Telegram ve Discord için hazır setlerin bulunduğu Hizmetler grubu. Kendi setinizi bağlantıyla ekleyebilirsiniz.

## Bağlantılar

Bağlantılar, ERKIN bağlıyken uygulamaların nereye gittiğini gösterir: oturumun kanal başına trafiği ve adresleriyle birlikte uygulamalar. Yalnızca ERKIN üzerinden giden uygulamalar, yani Bypass’takiler dışındaki tüm uygulamalar görünür.

- Bir uygulamada açtığı adresler, ne kadar trafik gittiği ve hangi kanal ya da kuralla gittiği görünür.
- Bir adresin ⋮ menüsü yalnızca bu uygulama için o adres ya da tüm site için kural oluşturur. Kurallar sekmesi uygulamanın kurallarını listeler.
