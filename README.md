# Root HID Remote

Android telefondan başka bir cihaza klavye ve mouse HID raporları göndermek için APK projesi.

## Neler var?

- **Root + `/dev/hidg*` USB gadget modu:** Uygulama `su` ile `/dev/hidg0` dosyasına klavye, `/dev/hidg1` dosyasına mouse raporu yazar.
- **Bluetooth HID denemesi:** Android `BluetoothHidDevice` profili açılabiliyor mu diye kontrol eder. Çoğu stock ROM'da bu profil `BLUETOOTH_PRIVILEGED` istediği için APK'nın privileged/system app olarak kurulması veya custom ROM gerekir.
- **Hazır UI:** Durum kontrolü, yazı gönderme, touchpad ile mouse hareketi, sol tık ve sağ tık.
- **USB gadget hazırlama scripti:** `scripts/setup-usb-hid-gadget.sh` configfs üstünden keyboard + mouse gadget oluşturur.

## Hızlı kullanım

1. APK'yı derle ve telefona kur.
2. USB HID için telefonda root shell açıp scripti çalıştır:

   ```sh
   su -c /data/local/tmp/setup-usb-hid-gadget.sh
   ```

3. Uygulamada **Bluetooth / root durumunu kontrol et** butonuna bas.
4. Hedef cihaza USB ile bağlan veya privileged Bluetooth HID destekliyorsa Bluetooth eşleştirmeyi hedef cihazdan başlat.
5. Yazı kutusundan klavye, pad alanından mouse gönder.

## Derleme

```bash
gradle :app:assembleDebug
```

Oluşan APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Bluetooth hakkında önemli not

Root, Android'in public Bluetooth HID Device API kısıtını otomatik kaldırmaz. Stock Android cihazların çoğunda `BLUETOOTH_PRIVILEGED` yalnızca sistem imzalı/privileged uygulamalara verilir. Bu yüzden Bluetooth tarafı uygulamada algılanır ve durum mesajı gösterir; gerçek Bluetooth HID için APK'yı `/system/priv-app` içine privileged olarak kurmanız veya ROM tarafında izni vermeniz gerekir.

