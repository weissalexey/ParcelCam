# ParcelCam (Android)

Simple camera app for parcel photos:
- Live camera preview (CameraX)
- 3 buttons: Settings / Capture / Switch camera
- Required number of photos (app won't exit until done)
- Timestamp watermark (date+time written into JPEG)
- Quality controls: JPEG quality + max resolution
- Upload methods: SMB (Windows share, domain auth), FTP, FTPS, SFTP
- Optional: save to Gallery
- Optional: delete local photos after successful upload
- After successful upload: app closes completely

## Build
Open folder `ParcelCam` in Android Studio (Giraffe+ / Jellyfish+).
Sync Gradle, run on device (Android 10/11/13 supported).

## Launch parameters (from another app)
Use Intent extras:
- `EXTRA_BASENAME` (String): base filename, e.g. "Order_4711"
- `EXTRA_REQUIRED_COUNT` (Int, optional): override required photo count

Example (Kotlin):
```
val i = Intent().setClassName("com.carstensen.parcelcam", "com.carstensen.parcelcam.ui.CameraActivity")
i.putExtra("EXTRA_BASENAME", "Order_4711")
i.putExtra("EXTRA_REQUIRED_COUNT", 5)
startActivity(i)
```

## Deep link (optional)
`parcelcam://capture?name=Order_4711&count=5`
(count currently not used in deep link; can be added easily)

## Notes
- SMB uses `jcifs-ng`. Credentials support both `DOMAIN\user` and `user@domain.tld`.
- SFTP host key verification is set to accept-all for MVP. For production, pin host keys.
