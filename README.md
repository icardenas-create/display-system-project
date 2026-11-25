# Display System

A full-screen image slideshow app for the Toshiba Fire TV (40V35, Fire OS 8). It replaces the
browser-based slideshow, so the browser's white menu bar no longer appears.

- Shows the images from a shared Google Drive folder. It checks for changes every 5 minutes.
- Saves the images on the TV, so the slideshow keeps working without internet.
- Plays images in file-name order. Name files `01 ...`, `02 ...` to set the order.
- Rotates the picture for a vertical TV, in any direction.
- Time per image is adjustable. "Static" stays on one image.
- Keeps the screen awake and opens by itself when the TV turns on.

## One-time setup (Mac on the same Wi-Fi as the TV)

1. On the TV, turn on **Settings > My Fire TV > Developer Options > ADB debugging**.
2. In Google Drive, make a folder for the display images. Share it as
   **Anyone with the link: Viewer** and copy the link.
3. On GitHub, open this repository's **Releases > Latest build** and download
   `display-system.apk` and `setup-tv.sh` into the same folder.
4. Open **Terminal**, go to that folder, and run:

   ```bash
   bash setup-tv.sh
   ```

5. The script asks for the TV's IP address. The first time, the TV asks
   "Allow USB debugging?". Choose **Always allow from this computer**, then run the script again.
6. Answer the questions: Drive folder link, seconds per image, rotation.

Run the same steps again to install a newer version. Settings and saved images are kept.

## Daily use

- **Change the images:** add, rename, or delete files in the Drive folder. The TV picks up
  changes within 5 minutes, or right away with **Check for new images now** in settings.
  To change an image, upload it as a new file and delete the old one.
- **Remote control:**
  - **OK** or **Menu**: open settings
  - **Left / Right**: previous / next image
  - **Back**: does nothing, so the display can't be closed by accident. **Home** leaves the app.
- **In settings:** Up/Down chooses a row, Left/Right changes it, Back closes.

## If the TV still turns off

The app keeps the screen awake while it runs. If the TV still powers off, check the TV's own
power settings: **Settings > Display & Audio > Power Controls** (names vary). Turn off any
sleep timer or auto power off.

## For developers

- Code: `app/src/main/java/com/displaysystem/app/`
- Every push to `main` runs the tests, builds the app on GitHub Actions, and replaces the
  **latest** release.
- Settings can also be sent with adb, for example:

  ```bash
  adb shell "am start -n com.displaysystem.app/.MainActivity --es folder 'DRIVE_LINK' --ei seconds 10 --ei rotation 90"
  ```

- The signing key in `app/signing/` is committed on purpose so every build installs over the
  previous one. Keep this repository private.
