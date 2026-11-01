# ERKIN Privacy Policy

Effective date: November 1, 2026

ERKIN is an open-source split-tunneling network tool for Android, Windows and Linux. The source code is published at https://github.com/yulbax/ERKIN under the GPL-3.0 license, so every statement below can be checked against it.

## Summary

ERKIN has no accounts, no analytics, no advertising and no crash reporting. The developer does not run any server for the app and receives no data from it.

## Data stored on your device

The app keeps the following only in its private storage on your device:

- servers, subscriptions and their addresses that you add;
- routing rules, rule lists and the apps you assign to each channel;
- ByeDPI strategies and their test results;
- app settings;
- a size-limited diagnostic log, which you can view and clear in the app.

This data is excluded from Android cloud backup and device-to-device transfer. It is deleted when you uninstall the app or clear its data. It leaves the device only when you export or share it yourself, and then only to the destination you choose.

## Installed apps

On Android ERKIN reads the list of installed apps (the `QUERY_ALL_PACKAGES` permission) so that you can choose which apps go through a server, through ByeDPI or directly. The list is used only on the device. Only the package names of the apps you assign are saved, and they are never transmitted.

## Your network traffic

ERKIN uses Android's `VpnService` to route the traffic of the apps you select:

- traffic sent to a server goes to the proxy or VPN server that you configured. Those servers are run by you or by a third party you chose, and their own privacy policies apply to them;
- traffic sent through ByeDPI is processed locally on the device and goes to its destination directly;
- traffic of other apps is not touched.

ERKIN does not record, inspect, sell or share the content of your traffic or the sites you visit.

## Network requests made by the app itself

Apart from routing your traffic, the app connects to these services. Like any internet connection, each of them can see the IP address the request comes from.

- **Subscription addresses you add**, to download the server list.
- **Rule lists you enable**: raw.githubusercontent.com (GitHub), small.oisd.nl.
- **Connection checks**: www.gstatic.com and www.youtube.com, to check that the connection works; www.cloudflare.com, to show the country of the server you are connected to.
- **ByeDPI tests**, when you run them: the sites listed in the test (YouTube, Google and other popular services) are opened to check which strategy works.
- **DNS**: domain names are resolved through the DNS servers set in the app settings, by default Cloudflare (1.1.1.1).
- **Updates**: the version from GitHub checks api.github.com for new releases. The Google Play version does not check for updates and installs them only through Google Play.

## Children

ERKIN is not directed at children and does not knowingly collect any data from anyone.

## Changes

Changes to this policy are published in this file. Its history is available in the repository.

## Contact

Questions about this policy can be asked at https://github.com/yulbax/ERKIN/issues.

---

# Политика конфиденциальности ERKIN

Дата вступления в силу: 1 ноября 2026 г.

ERKIN — сетевой инструмент для раздельного туннелирования на Android, Windows и Linux с открытым исходным кодом. Код опубликован на https://github.com/yulbax/ERKIN под лицензией GPL-3.0, поэтому всё сказанное ниже можно проверить по нему.

## Кратко

В ERKIN нет учётных записей, аналитики, рекламы и отчётов о сбоях. У разработчика нет серверов для приложения, и он не получает от него никаких данных.

## Данные на устройстве

Приложение хранит только в своём закрытом хранилище на устройстве:

- добавленные вами серверы, подписки и их адреса;
- правила маршрутизации, списки правил и распределение приложений по каналам;
- стратегии ByeDPI и результаты их проверки;
- настройки;
- диагностический журнал ограниченного размера, который можно посмотреть и очистить в приложении.

Эти данные не попадают в облачные резервные копии Android и не переносятся на другое устройство. Они удаляются вместе с приложением или при очистке его данных. С устройства они уходят, только если вы сами экспортируете их или поделитесь ими, и только туда, куда вы выберете.

## Установленные приложения

На Android ERKIN читает список установленных приложений (разрешение `QUERY_ALL_PACKAGES`), чтобы вы могли выбрать, какие приложения идут через сервер, через ByeDPI или напрямую. Список используется только на устройстве. Сохраняются лишь имена пакетов приложений, которые вы распределили, и они никуда не передаются.

## Ваш сетевой трафик

ERKIN использует `VpnService` Android, чтобы направлять трафик выбранных приложений:

- трафик через сервер идёт на прокси- или VPN-сервер, который вы указали. Эти серверы принадлежат вам или выбранной вами третьей стороне, и к ним применяются их собственные политики;
- трафик через ByeDPI обрабатывается локально на устройстве и идёт к адресату напрямую;
- трафик остальных приложений не затрагивается.

ERKIN не записывает, не анализирует, не продаёт и не передаёт содержимое вашего трафика и список посещаемых сайтов.

## Сетевые запросы самого приложения

Помимо маршрутизации вашего трафика, приложение обращается к следующим сервисам. Как и при любом подключении к интернету, каждый из них видит IP-адрес, с которого пришёл запрос.

- **Адреса подписок, которые вы добавили**, — чтобы загрузить список серверов.
- **Включённые вами списки правил**: raw.githubusercontent.com (GitHub), small.oisd.nl.
- **Проверка подключения**: www.gstatic.com и www.youtube.com — чтобы убедиться, что соединение работает; www.cloudflare.com — чтобы показать страну сервера, к которому вы подключены.
- **Проверка ByeDPI**, когда вы её запускаете: открываются сайты из списка проверки (YouTube, Google и другие популярные сервисы), чтобы определить, какая стратегия работает.
- **DNS**: доменные имена разрешаются через DNS-серверы из настроек приложения, по умолчанию Cloudflare (1.1.1.1).
- **Обновления**: версия с GitHub проверяет новые выпуски на api.github.com. Версия из Google Play обновления не проверяет и устанавливает их только через Google Play.

## Дети

ERKIN не предназначен для детей и ни у кого сознательно не собирает данные.

## Изменения

Изменения политики публикуются в этом файле, история изменений доступна в репозитории.

## Контакты

Вопросы о политике можно задать на https://github.com/yulbax/ERKIN/issues.
