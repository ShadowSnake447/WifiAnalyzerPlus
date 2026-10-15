# WiFi Analyzer+ Flipper Tools v3.0

## Zahrnuté funkce
- Wi-Fi Scanner PRO: okolní sítě, BSSID, dBm, pásmo, kanál a graf 2,4 GHz; ruční obnovení.
- Bluetooth LE scanner: skenování reklamních BLE zařízení po dobu 10 sekund, název/adresa/RSSI podle dostupných oprávnění.
- Síťový diagnostik: aktivní rozhraní, lokální IP, výchozí brána, DNS a test dostupnosti brány.
- ESP32 / Wi-Fi Devboard: zobrazení připojených USB zařízení VID/PID. USB OTG a kompatibilní hardware nutné; univerzální sériový protokol není automaticky zaručen.
- Custom NFC Reader & Editor: automatická identifikace tag ID a technologií, čtení NDEF textu/URL a obecných záznamů, kontrola zapisovatelnosti a kapacity; bezpečný zápis textu nebo URL po potvrzení na existující zapisovatelné NDEF tagy. Tagy bez NDEF se automaticky neformátují.
- IR: kontrola vestavěného vysílače a podporovaných nosných frekvencí; odesílání surového IR časování přes Android `ConsumerIrManager`. Uživatel zadává frekvenci a kompletní pulzní sekvenci v mikrosekundách. Ukázka NEC je pouze začátek rámce, nikoli funkční příkaz pro konkrétní zařízení.
- Sub-GHz: detekce připojených USB zařízení jako první diagnostický krok. Externí rádiový modul/SDR a specifický ovladač jsou nutné; příjem/vysílání Sub-GHz dat není v této verzi implementováno.
- Tmavý / světlý režim.

## Sestavení APK
1. Rozbal ZIP.
2. Otevři složku `WifiAnalyzerFlipper` v Android Studiu.
3. Nech dokončit Gradle Sync; potřebuje Android SDK 35 a internet.
4. Build → Build Bundle(s) / APK(s) → Build APK(s).
5. Výstup: `app/build/outputs/apk/debug/app-debug.apk`.

## Omezení / bezpečnost
Android může omezit četnost Wi-Fi skenování a vyžaduje příslušná oprávnění. BLE zařízení nemusí vysílat jméno. Ping dostupnosti může být blokován firewallem. USB zařízení jsou pouze vypsána, nikoliv ovládána univerzálním protokolem. Aplikace neprovádí rušení sítí, kopírování přístupových karet ani neoprávněné rádiové vysílání.

## IR ovládání
Zadej nosnou frekvenci (například 38000 Hz, pokud ji zařízení používá) a kompletní sekvenci délek pulzů/mezer v mikrosekundách. Aplikace ji odešle přes vestavěný IR vysílač. Neexistuje jeden univerzální příkaz pro TV/klimatizace; chybné časování nebude fungovat. Testuj pouze zařízení, která smíš ovládat.

## Custom NFC Reader & Editor
Používá vestavěný NFC čip Androidu. Přečte UID, technologie a dostupné NDEF záznamy. U podporovaných NDEF tagů ukazuje zapisovatelnost/kapacitu a umí zapsat text nebo URL po potvrzovacím dialogu. Zápis přepíše stávající NDEF zprávu. Aplikace tagy bez NDEF automaticky neformátuje, neobchází klíče ani neupravuje zabezpečené přístupové karty.
