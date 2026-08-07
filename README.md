# Root HID Remote

Android telefondan başka bir cihaza klavye ve mouse HID raporları göndermek için APK projesi.

## Neler var?

- **Root + `/dev/hidg*` USB gadget modu:** Uygulama `su` ile `/dev/hidg0` dosyasına klavye, `/dev/hidg1` dosyasına mouse raporu yazar.
- **Bluetooth HID denemesi:** Android `BluetoothHidDevice` profili açılabiliyor mu diye kontrol eder. Çoğu stock ROM'da bu profil `BLUETOOTH_PRIVILEGED` istediği için APK'nın privileged/system app olarak kurulması veya custom ROM gerekir.
- **Hazır UI:** Durum kontrolü, Bluetooth cihaz tarama/seçme/bağlanma, yazı gönderme, touchpad ile mouse hareketi, sol tık ve sağ tık.
- **USB gadget hazırlama scripti:** `scripts/setup-usb-hid-gadget.sh` configfs üstünden keyboard + mouse gadget oluşturur.

## Hızlı kullanım

1. APK'yı derle ve telefona kur.
2. USB HID için telefonda root shell açıp scripti çalıştır:

   ```sh
   su -c /data/local/tmp/setup-usb-hid-gadget.sh
   ```

3. Uygulamada **Bluetooth / root durumunu kontrol et** butonuna bas.
4. Bluetooth için **Bluetooth cihazlarını tara / yenile** ile cihazları listele, listeden hedefi seç ve **Seçili Bluetooth cihaza bağlan** butonuna bas.
5. USB için hedef cihaza kabloyla bağlan; Bluetooth için privileged HID destekliyorsa eşleştirme/bağlanma isteğini hedef cihazda onayla.
6. Bağlantıdan sonra uygulama aktif çıkışı gösterir; Bluetooth bağlıysa yazı ve mouse pad Bluetooth HID üzerinden, bağlı değilse root `/dev/hidg*` üzerinden gönderilir.

## Derleme

```bash
gradle :app:assembleDebug
```

Oluşan APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Bluetooth hakkında önemli not

Root, Android'in public Bluetooth HID Device API kısıtını otomatik kaldırmaz. Stock Android cihazların çoğunda `BLUETOOTH_PRIVILEGED` yalnızca sistem imzalı/privileged uygulamalara verilir. Bu yüzden Bluetooth tarafı uygulamada algılanır ve durum mesajı gösterir; gerçek Bluetooth HID için APK'yı `/system/priv-app` içine privileged olarak kurmanız veya ROM tarafında izni vermeniz gerekir.

## Güvenlik

Bu uygulama güçlü root ve HID yetenekleri kullanır. Sadece kendi cihazlarınızda, izinli testlerde ve yasal amaçlarla kullanın.
