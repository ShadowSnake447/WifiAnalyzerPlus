package cz.honza.wifianalyzer;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;
import android.net.*;
import android.net.wifi.*;
import android.bluetooth.*;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.nfc.*;
import android.nfc.tech.*;
import android.hardware.ConsumerIrManager;
import android.hardware.usb.*;
import android.os.*;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private WifiManager wifi;
    private BluetoothAdapter btAdapter;
    private BluetoothLeScanner bleScanner;
    private ScanCallback bleCallback;
    private NfcAdapter nfcAdapter;
    private ConsumerIrManager irManager;
    private UsbManager usbManager;
    private LinearLayout root, body, wifiList, bleList;
    private TextView status, wifiStatus, networkResult, usbResult, nfcResult, irResult, subResult;
    private EditText irFrequencyInput, irPatternInput, nfcEditorInput;
    private Tag lastNfcTag;
    private ChannelGraph graph;
    private boolean dark = true;
    private int scanCount = 0;
    private static final int REQ = 77;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private int bg() { return dark ? Color.rgb(14,18,30) : Color.rgb(245,247,251); }
    private int cardBg() { return dark ? Color.rgb(27,34,52) : Color.WHITE; }
    private int primary() { return dark ? Color.WHITE : Color.rgb(25,35,55); }
    private int secondary() { return dark ? Color.rgb(190,202,225) : Color.rgb(80,92,112); }
    private int accent() { return dark ? Color.rgb(96,165,250) : Color.rgb(37,99,235); }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        wifi = (WifiManager)getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        BluetoothManager bm = (BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
        if (bm != null) btAdapter = bm.getAdapter();
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        irManager = (ConsumerIrManager)getSystemService(CONSUMER_IR_SERVICE);
        usbManager = (UsbManager)getSystemService(USB_SERVICE);
        buildUi();
        requestPermissionsIfNeeded();
        handleNfcIntent(getIntent());
    }

    private TextView text(String s, int size, boolean bold) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(size);
        t.setTextColor(primary()); if (bold) t.setTypeface(null, 1); return t;
    }
    private Button button(String s, Runnable r) {
        Button b = new Button(this); b.setText(s); b.setOnClickListener(v -> r.run()); return b;
    }
    private LinearLayout section(String title) {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12),dp(10),dp(12),dp(10)); card.setBackgroundColor(cardBg());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,0,0,dp(10));
        body.addView(card,lp); TextView t=text(title,17,true); card.addView(t);
        return card;
    }
    private void addButton(LinearLayout card, String label, Runnable r) {
        card.addView(button(label,r), new LinearLayout.LayoutParams(-1,-2));
    }
    private TextView info(LinearLayout card, String s) {
        TextView t=text(s,13,false); t.setTextColor(secondary()); t.setPadding(0,dp(5),0,dp(5)); card.addView(t); return t;
    }

    private void buildUi() {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16),dp(20),dp(16),dp(10)); root.setBackgroundColor(bg());
        LinearLayout header = new LinearLayout(this); header.setOrientation(LinearLayout.HORIZONTAL);
        TextView title=text("WiFi Analyzer+",26,true); header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Button theme=button(dark?"☀":"☾",()->{dark=!dark;buildUi();}); header.addView(theme);
        root.addView(header);
        TextView subtitle=text("Flipper-inspired diagnostics • v3.0",13,false);
        subtitle.setTextColor(secondary()); root.addView(subtitle);
        status=text("Diagnostické nástroje",13,false); status.setTextColor(accent()); status.setPadding(0,dp(5),0,dp(8)); root.addView(status);
        ScrollView scroll=new ScrollView(this); body=new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0,dp(4),0,0); scroll.addView(body); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout w=section("📶 Wi-Fi Scanner PRO");
        info(w,"Sítě v okolí, kanál, pásmo, BSSID a síla signálu.");
        addButton(w,"⟳ Skenovat / obnovit Wi-Fi",()->startWifiScan());
        wifiStatus=info(w,"Zatím bez výsledků");
        graph=new ChannelGraph(this); w.addView(graph,new LinearLayout.LayoutParams(-1,dp(150)));
        wifiList=new LinearLayout(this); wifiList.setOrientation(LinearLayout.VERTICAL); w.addView(wifiList);

        LinearLayout b=section("ᛒ Bluetooth / BLE scanner");
        info(b,"Vyhledá inzerující BLE zařízení. Název nemusí být dostupný; RSSI je orientační.");
        addButton(b,"Hledat BLE zařízení (10 s)",()->startBleScan());
        bleList=new LinearLayout(this); bleList.setOrientation(LinearLayout.VERTICAL); b.addView(bleList);

        LinearLayout n=section("🌐 Síťový diagnostik");
        info(n,"Informace o připojené síti a ping výchozí brány. Kontrola zařízení je omezena na lokální síť.");
        addButton(n,"Zkontrolovat síť + ping brány",()->runNetworkDiagnostics());
        networkResult=info(n,"Stiskni tlačítko pro kontrolu.");

        LinearLayout u=section("🔌 ESP32 / Wi-Fi Devboard — EXTERNÍ HARDWARE");
        info(u,"Vyžaduje USB OTG adaptér a kompatibilní USB sériový převodník / desku. Protokol závisí na firmware.");
        addButton(u,"Zobrazit USB zařízení",()->showUsbDevices());
        usbResult=info(u,"USB zařízení zatím nezkontrolována.");

        LinearLayout nf=section("💳 Custom NFC Reader & Editor");
        info(nf,"Automaticky rozpozná dostupné NFC technologie, přečte NDEF text/URL a zapíše nový text nebo URL na podporované zapisovatelné NDEF tagy. Neobchází zabezpečení karet.");
        addButton(nf,"Aktivovat NFC čtečku",()->enableNfc());
        nfcResult=info(nf, nfcAdapter==null ? "Tento telefon NFC nepodporuje." : "Zapni NFC a přilož tag.");
        TextView nfcLabel=text("Obsah k zápisu (text nebo URL)",13,true); nfcLabel.setPadding(0,dp(8),0,0); nf.addView(nfcLabel);
        nfcEditorInput=new EditText(this); nfcEditorInput.setMinLines(2); nfcEditorInput.setGravity(Gravity.TOP);
        nfcEditorInput.setHint("Text nebo https://example.com");
        nf.addView(nfcEditorInput,new LinearLayout.LayoutParams(-1,-2));
        addButton(nf,"Zapsat text / URL na načtený tag",()->confirmNfcWrite());
        info(nf,"Zápis je dostupný pouze na kompatibilních NDEF tagech, které nejsou zamčené a mají dost kapacity. Před zápisem se zobrazí potvrzení.");

        LinearLayout ir=section("📺 Infračervené ovládání — vestavěný IR");
        info(ir,"Odesílá surové IR pulzy přes Android ConsumerIrManager. Zadej kód svého zařízení; univerzální power kód neexistuje.");
        addButton(ir,"Zkontrolovat IR hardware",()->checkIr());
        irResult=info(ir,"Nejprve ověř podporu vysílače.");
        TextView freqLabel=text("Nosná frekvence (Hz)",13,true); ir.addView(freqLabel);
        irFrequencyInput=new EditText(this); irFrequencyInput.setSingleLine(true);
        irFrequencyInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        irFrequencyInput.setText("38000"); irFrequencyInput.setHint("např. 38000");
        ir.addView(irFrequencyInput,new LinearLayout.LayoutParams(-1,-2));
        TextView patternLabel=text("Časování pulzů v mikrosekundách",13,true); patternLabel.setPadding(0,dp(8),0,0); ir.addView(patternLabel);
        irPatternInput=new EditText(this); irPatternInput.setMinLines(3); irPatternInput.setGravity(Gravity.TOP);
        irPatternInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        irPatternInput.setHint("např. 9000,4500,560,560,560,1690,560,560");
        ir.addView(irPatternInput,new LinearLayout.LayoutParams(-1,-2));
        info(ir,"Vlož přesné časování v µs získané pro svůj ovladač/protokol. Hodnoty střídají délky IR pulzů a mezer.");
        addButton(ir,"Odeslat IR příkaz",()->transmitIr());
        addButton(ir,"Vložit ukázkový NEC začátek",()->irPatternInput.setText("9000,4500,560,560,560,1690,560,560"));
        info(ir,"Ukázka je pouze začátek NEC rámce, ne kompletní příkaz pro konkrétní zařízení.");

        LinearLayout sub=section("📻 Sub-GHz — EXTERNÍ HARDWARE");
        info(sub,"Vyžaduje kompatibilní externí přijímač / SDR nebo modul přes USB. Tato verze pouze kontroluje USB zařízení; nevysílá rádiové signály.");
        addButton(sub,"Zkontrolovat připojený modul",()->showSubGhz());
        subResult=info(sub,"Modul zatím nezkontrolován.");
        setContentView(root);
    }

    private void requestPermissionsIfNeeded() {
        ArrayList<String> p=new ArrayList<>();
        if (Build.VERSION.SDK_INT>=33) {
            if(checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES)!=PackageManager.PERMISSION_GRANTED) p.add(Manifest.permission.NEARBY_WIFI_DEVICES);
            if(checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)!=PackageManager.PERMISSION_GRANTED) p.add(Manifest.permission.BLUETOOTH_SCAN);
            if(checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED) p.add(Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) p.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if(Build.VERSION.SDK_INT>=31 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) p.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if(!p.isEmpty()) requestPermissions(p.toArray(new String[0]),REQ);
    }
    @Override public void onRequestPermissionsResult(int rc,String[] ps,int[] rs) {
        super.onRequestPermissionsResult(rc,ps,rs);
        if(rc==REQ) { boolean ok=true; for(int x:rs) if(x!=PackageManager.PERMISSION_GRANTED) ok=false;
            status.setText(ok?"Oprávnění udělena. Vyber nástroj níže.":"Některé funkce potřebují oprávnění v nastavení Androidu."); }
    }

    private void startWifiScan() {
        if(wifi==null || !wifi.isWifiEnabled()) {
            wifiStatus.setText("Wi-Fi je vypnutá nebo není dostupná. Otevírám nastavení.");
            startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)); return;
        }
        try { wifi.startScan(); showWifiResults(); }
        catch(SecurityException e) { wifiStatus.setText("Chybí oprávnění k Wi-Fi skenování."); }
    }
    private void showWifiResults() {
        wifiList.removeAllViews();
        try {
            List<ScanResult> results=wifi.getScanResults();
            Collections.sort(results,(a,b)->Integer.compare(b.level,a.level));
            scanCount++; wifiStatus.setText("Nalezeno sítí: "+results.size()+" • aktualizace #"+scanCount+" (Android může vracet cache)");
            graph.setResults(results);
            for(ScanResult r:results) {
                String band=r.frequency>=5925?"6 GHz":(r.frequency>=4900?"5 GHz":"2,4 GHz");
                String name=(r.SSID==null||r.SSID.isEmpty())?"(skrytá síť)":r.SSID;
                TextView t=text(name+"  •  "+r.level+" dBm\n"+r.BSSID+"  •  kanál "+channel(r.frequency)+" • "+band+"\n"+security(r.capabilities),13,true);
                t.setTextColor(secondary()); t.setPadding(0,dp(6),0,dp(6)); wifiList.addView(t);
            }
        } catch(Exception e) { wifiStatus.setText("Skenování není povoleno: "+e.getClass().getSimpleName()); }
    }
    private int channel(int f) {
        if(f==2484)return 14; if(f>=2412&&f<=2472)return (f-2407)/5;
        if(f>=5000&&f<5925)return (f-5000)/5; if(f>=5955)return (f-5950)/5; return 0;
    }
    private String security(String s) {
        if(s==null)return "Zabezpečení neznámé";
        if(s.contains("SAE"))return "WPA3"; if(s.contains("WPA2"))return "WPA2";
        if(s.contains("WPA"))return "WPA"; if(s.contains("WEP"))return "WEP"; return "Otevřená / jiné";
    }

    private void startBleScan() {
        if(btAdapter==null) { bleList.addView(text("Bluetooth není na telefonu dostupný.",13,false)); return; }
        if(!btAdapter.isEnabled()) { startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); return; }
        if(Build.VERSION.SDK_INT>=31 && checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissionsIfNeeded(); Toast.makeText(this,"Povol Bluetooth scan a zkus to znovu.",Toast.LENGTH_LONG).show(); return;
        }
        bleList.removeAllViews();
        bleList.addView(text("Skenování probíhá 10 sekund…",13,false));
        bleScanner=btAdapter.getBluetoothLeScanner();
        if(bleScanner==null) { bleList.addView(text("BLE scanner není dostupný.",13,false)); return; }
        final Map<String,String> found=new LinkedHashMap<>();
        bleCallback=new ScanCallback() {
            @Override public void onScanResult(int type, android.bluetooth.le.ScanResult result) {
                BluetoothDevice d=result.getDevice();
                String addr=d.getAddress(), name="BLE zařízení";
                try { if(d.getName()!=null) name=d.getName(); } catch(SecurityException ignored) {}
                found.put(addr,name+" • "+addr+" • RSSI "+result.getRssi()+" dBm");
                runOnUiThread(()->renderBle(found));
            }
            @Override public void onScanFailed(int error) { runOnUiThread(()->bleList.addView(text("BLE scan selhal: "+error,13,false))); }
        };
        try {
            bleScanner.startScan(bleCallback);
            handler.postDelayed(()->{ try { if(bleScanner!=null&&bleCallback!=null) bleScanner.stopScan(bleCallback); } catch(Exception ignored){} },10000);
        } catch(SecurityException e) { bleList.addView(text("Chybí Bluetooth oprávnění.",13,false)); }
    }
    private void renderBle(Map<String,String> found) {
        bleList.removeAllViews();
        if(found.isEmpty()) { bleList.addView(text("Zatím žádná zařízení. Zkus znovu v blízkosti zařízení.",13,false)); return; }
        for(String s:found.values()) { TextView t=text(s,13,false); t.setTextColor(secondary()); bleList.addView(t); }
    }

    private void runNetworkDiagnostics() {
        networkResult.setText("Zjišťuji síť a testuji bránu…");
        new Thread(()->{
            StringBuilder out=new StringBuilder();
            try {
                ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
                Network nw=cm.getActiveNetwork(); LinkProperties lp=nw==null?null:cm.getLinkProperties(nw);
                NetworkCapabilities nc=nw==null?null:cm.getNetworkCapabilities(nw);
                out.append("Připojení: ").append(nc!=null&&nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)?"Wi-Fi":nc==null?"offline":"jiné").append("\n");
                if(lp!=null) {
                    out.append("Rozhraní: ").append(lp.getInterfaceName()).append("\n");
                    for(LinkAddress a:lp.getLinkAddresses()) out.append("IP: ").append(a.getAddress().getHostAddress()).append("\n");
                    InetAddress gateway=null;
                    for(RouteInfo route:lp.getRoutes()) if(route.isDefault()&&route.getGateway()!=null){gateway=route.getGateway();break;}
                    if(gateway!=null) {
                        out.append("Brána: ").append(gateway.getHostAddress()).append("\n");
                        long start=System.currentTimeMillis();
                        boolean reachable=gateway.isReachable(1800);
                        out.append("Ping / dostupnost brány: ").append(reachable?"odpovídá":"bez odpovědi").append(" (").append(System.currentTimeMillis()-start).append(" ms)\n");
                    } else out.append("Výchozí brána nebyla nalezena.\n");
                    out.append("DNS: "); for(InetAddress d:lp.getDnsServers()) out.append(d.getHostAddress()).append("  ");
                } else out.append("Žádné aktivní síťové připojení.");
            } catch(Exception e) { out.append("Chyba diagnostiky: ").append(e.getMessage()); }
            final String result=out.toString();
            runOnUiThread(()->networkResult.setText(result));
        }).start();
    }

    private void showUsbDevices() {
        Map<String,UsbDevice> devices=usbManager.getDeviceList();
        if(devices.isEmpty()) { usbResult.setText("Nebylo nalezeno žádné USB zařízení. Připoj desku přes USB OTG."); return; }
        StringBuilder b=new StringBuilder();
        for(UsbDevice d:devices.values()) b.append(d.getDeviceName()).append("\nVID: ").append(d.getVendorId()).append(" • PID: ").append(d.getProductId()).append("\n");
        usbResult.setText(b.toString()+"\nPozn.: nalezení zařízení neznamená, že aplikace zná jeho sériový protokol.");
    }
    private void showSubGhz() {
        Map<String,UsbDevice> devices=usbManager.getDeviceList();
        if(devices.isEmpty()) subResult.setText("Žádný USB modul. Vyžaduje externí kompatibilní přijímač / SDR a odpovídající ovladač.");
        else subResult.setText("USB zařízení nalezena: "+devices.size()+". Zkontroluj jejich VID/PID v sekci ESP32. Přenos rádiových dat zatím není implementován.");
    }

    private void enableNfc() {
        if(nfcAdapter==null) { nfcResult.setText("Telefon NFC nepodporuje."); return; }
        if(!nfcAdapter.isEnabled()) { nfcResult.setText("Zapni NFC v nastavení telefonu."); startActivity(new Intent(Settings.ACTION_NFC_SETTINGS)); return; }
        nfcResult.setText("NFC je zapnuté. Přilož tag zadní stranou telefonu. Automatická identifikace proběhne při načtení.");
    }
    @Override protected void onResume() {
        super.onResume();
        if(nfcAdapter!=null && nfcAdapter.isEnabled()) {
            try {
                Intent i=new Intent(this,getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                PendingIntent pi=PendingIntent.getActivity(this,0,i,PendingIntent.FLAG_MUTABLE);
                nfcAdapter.enableForegroundDispatch(this,pi,null,null);
            } catch(Exception ignored) {}
        }
    }
    @Override protected void onPause() {
        super.onPause();
        try { if(nfcAdapter!=null) nfcAdapter.disableForegroundDispatch(this); } catch(Exception ignored) {}
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent); handleNfcIntent(intent);
    }
    private void handleNfcIntent(Intent intent) {
        if(intent==null)return;
        Tag tag;
        if(Build.VERSION.SDK_INT>=33) tag=intent.getParcelableExtra(NfcAdapter.EXTRA_TAG,Tag.class);
        else { //noinspection deprecation
            tag=intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        }
        if(tag==null)return;
        lastNfcTag=tag;
        StringBuilder s=new StringBuilder("NFC tag načten\nID: ");
        byte[] id=tag.getId(); for(byte v:id)s.append(String.format("%02X",v));
        s.append("\nTechnologie: "); for(String tech:tag.getTechList())s.append(tech.substring(tech.lastIndexOf('.')+1)).append(" ");
        boolean readableNdef=false;
        Ndef ndef=null;
        try {
            ndef=Ndef.get(tag);
            if(ndef!=null) {
                ndef.connect();
                s.append("\nTyp: NDEF");
                s.append("\nZapisovatelný: ").append(ndef.isWritable()?"ano":"ne");
                s.append("\nKapacita: ").append(ndef.getMaxSize()).append(" B");
                NdefMessage msg=ndef.getNdefMessage();
                if(msg!=null) {
                    readableNdef=true;
                    s.append("\nZáznamy NDEF: ").append(msg.getRecords().length);
                    for(NdefRecord rec:msg.getRecords()) {
                        try {
                            if(rec.getTnf()==NdefRecord.TNF_WELL_KNOWN && Arrays.equals(rec.getType(),NdefRecord.RTD_TEXT)) {
                                byte[] p=rec.getPayload();
                                if(p.length>0) {
                                    int lang=p[0]&0x3f;
                                    if(1+lang<=p.length)s.append("\nText: ").append(new String(p,1+lang,p.length-1-lang,"UTF-8"));
                                }
                            } else if(rec.getTnf()==NdefRecord.TNF_WELL_KNOWN && Arrays.equals(rec.getType(),NdefRecord.RTD_URI)) {
                                s.append("\nURL: ").append(new String(rec.getPayload(),"UTF-8"));
                            } else {
                                s.append("\nZáznam: TNF ").append(rec.getTnf()).append(", typ ").append(new String(rec.getType(),"UTF-8")).append(", ").append(rec.getPayload().length).append(" B");
                            }
                        } catch(Exception parseError) { s.append("\nNDEF záznam nelze dekódovat."); }
                    }
                } else s.append("\nNDEF obsah: prázdný");
                ndef.close();
            } else {
                s.append("\nTento tag neposkytuje standardní NDEF rozhraní.");
                NdefFormatable fmt=NdefFormatable.get(tag);
                if(fmt!=null)s.append("\nTag může podporovat formátování do NDEF, ale aplikace ho automaticky neformátuje.");
            }
        } catch(Exception e) {
            s.append("\nČtení NDEF se nezdařilo: ").append(e.getClass().getSimpleName());
            try { if(ndef!=null)ndef.close(); } catch(Exception ignored) {}
        }
        if(nfcResult!=null)nfcResult.setText(s.toString());
    }

    private void confirmNfcWrite() {
        if(lastNfcTag==null) {
            nfcResult.setText("Nejdřív přilož tag, aby se načetl.");
            return;
        }
        String value=nfcEditorInput.getText().toString().trim();
        if(value.isEmpty()) { nfcResult.setText("Zadej text nebo URL."); return; }
        String kind=(value.matches("(?i)^https?://.+")?"URL":"text");
        new android.app.AlertDialog.Builder(this)
            .setTitle("Potvrdit zápis NFC")
            .setMessage("Na načtený NFC tag se zapíše "+kind+":\n\n"+value+"\n\nExistující NDEF obsah může být přepsán. Pokračovat?")
            .setNegativeButton("Zrušit", (d,w)->{})
            .setPositiveButton("Zapsat", (d,w)->writeNfc(value))
            .show();
    }

    private void writeNfc(String value) {
        Tag tag=lastNfcTag;
        if(tag==null) { nfcResult.setText("Tag už není dostupný. Přilož ho znovu."); return; }
        Ndef ndef=Ndef.get(tag);
        if(ndef==null) {
            nfcResult.setText("Tento tag neposkytuje NDEF zápis. Aplikace ho automaticky neformátuje ani neobchází zabezpečení.");
            return;
        }
        try {
            NdefRecord record;
            if(value.matches("(?i)^https?://.+")) {
                record=NdefRecord.createUri(value);
            } else {
                record=NdefRecord.createTextRecord("cs",value);
            }
            NdefMessage message=new NdefMessage(new NdefRecord[]{record});
            ndef.connect();
            if(!ndef.isWritable()) {
                nfcResult.setText("Tag je pouze pro čtení nebo je zamčený.");
                return;
            }
            int size=message.toByteArray().length;
            if(size>ndef.getMaxSize()) {
                nfcResult.setText("Obsah je příliš velký: "+size+" B, kapacita tagu "+ndef.getMaxSize()+" B.");
                return;
            }
            ndef.writeNdefMessage(message);
            nfcResult.setText("Zápis úspěšný. Uloženo "+size+" B jako "+(value.matches("(?i)^https?://.+")?"URL":"text")+". Přilož tag znovu pro ověření.");
        } catch(TagLostException e) {
            nfcResult.setText("Tag byl během zápisu odpojen. Přilož ho znovu a opakuj zápis.");
        } catch(FormatException e) {
            nfcResult.setText("Obsah NDEF není pro tento tag platný.");
        } catch(Exception e) {
            nfcResult.setText("Zápis se nezdařil: "+e.getClass().getSimpleName()+" — "+e.getMessage());
        } finally {
            try { ndef.close(); } catch(Exception ignored) {}
        }
    }

    private void checkIr() {
        if(irManager==null || !irManager.hasIrEmitter()) {
            irResult.setText("IR vysílač není dostupný přes Android ConsumerIrManager.");
            return;
        }
        StringBuilder s=new StringBuilder("IR vysílač dostupný.\nPodporované nosné frekvence: ");
        int[] ranges=irManager.getCarrierFrequencies();
        if(ranges!=null && ranges.length>=2) {
            for(int i=0;i+1<ranges.length;i+=2)s.append(ranges[i]).append("–").append(ranges[i+1]).append(" Hz; ");
        } else s.append("telefon neposkytl rozsahy");
        s.append("\nZadej kompletní časování příkazu pro své zařízení.");
        irResult.setText(s.toString());
    }

    private void transmitIr() {
        if(irManager==null || !irManager.hasIrEmitter()) {
            irResult.setText("IR vysílač není dostupný. Zkontroluj kompatibilitu telefonu.");
            return;
        }
        try {
            int frequency=Integer.parseInt(irFrequencyInput.getText().toString().trim());
            String raw=irPatternInput.getText().toString().trim();
            if(frequency<10000 || frequency>1000000) {
                irResult.setText("Neplatná frekvence. Zadej hodnotu v Hz, například 38000.");
                return;
            }
            String[] parts=raw.split("[,;\\s]+");
            if(parts.length<4 || parts.length>1000) {
                irResult.setText("Časování musí obsahovat 4 až 1000 kladných hodnot oddělených čárkou.");
                return;
            }
            int[] pattern=new int[parts.length];
            long total=0;
            for(int i=0;i<parts.length;i++) {
                pattern[i]=Integer.parseInt(parts[i]);
                if(pattern[i]<=0 || pattern[i]>100000) {
                    irResult.setText("Každý pulz musí být kladný a nejvýše 100000 µs.");
                    return;
                }
                total+=pattern[i];
            }
            if(total>500000) {
                irResult.setText("Příkaz je příliš dlouhý (nad 500 ms). Zkrať časování.");
                return;
            }
            irManager.transmit(frequency,pattern);
            irResult.setText("IR příkaz odeslán: "+frequency+" Hz, "+pattern.length+" pulzů, "+total+" µs. Pokud zařízení nereaguje, ověř protokol, frekvenci a směr telefonu.");
        } catch(NumberFormatException e) {
            irResult.setText("Zkontroluj frekvenci a časování — očekávám čísla oddělená čárkami.");
        } catch(SecurityException e) {
            irResult.setText("Android odmítl přístup k IR vysílači.");
        } catch(Exception e) {
            irResult.setText("Odeslání selhalo: "+e.getClass().getSimpleName()+" — "+e.getMessage());
        }
    }

    private class ChannelGraph extends View {
        private final Paint p=new Paint(3); private List<ScanResult> data=new ArrayList<>();
        ChannelGraph(Context c){super(c);}
        void setResults(List<ScanResult> r){data=new ArrayList<>(r);invalidate();}
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c); float w=getWidth(),h=getHeight(); c.drawColor(bg());
            p.setColor(secondary()); p.setTextSize(dp(10)); p.setStrokeWidth(dp(1));
            for(int ch=1;ch<=13;ch+=2){float x=dp(10)+(ch-1)*(w-dp(20))/12f;c.drawLine(x,dp(10),x,h-dp(20),p);c.drawText(""+ch,x-dp(3),h-dp(4),p);}
            for(ScanResult r:data) {
                if(r.frequency<2400||r.frequency>2500)continue; int ch=channel(r.frequency); if(ch<1||ch>13)continue;
                float x=dp(10)+(ch-1)*(w-dp(20))/12f;
                float y=dp(12)+(Math.max(-95,Math.min(-25,r.level))+25)/-70f*(h-dp(38));
                p.setColor(accent());p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2));
                c.drawLine(x,h-dp(20),x,Math.max(dp(12),Math.min(h-dp(22),y)),p);
                p.setStyle(Paint.Style.FILL);p.setTextSize(dp(9));c.drawText(r.level+"",x-dp(10),Math.max(dp(12),y-dp(3)),p);
            }
        }
    }
}
