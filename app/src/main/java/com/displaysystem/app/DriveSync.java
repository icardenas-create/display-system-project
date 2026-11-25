package com.displaysystem.app;

import android.graphics.BitmapFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Copies the images in a shared Google Drive folder to the TV.
 *
 * The folder must be shared as "Anyone with the link can view". The images are
 * saved on the TV, so the slideshow keeps working without internet.
 * Images play in file-name order, so name them "01 ...", "02 ..." to set the order.
 */
public class DriveSync {
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36";
    private static final Pattern FOLDER_IN_URL = Pattern.compile("/folders/([A-Za-z0-9_-]{10,})");
    private static final Pattern ID_PARAM = Pattern.compile("[?&]id=([A-Za-z0-9_-]{10,})");
    private static final Pattern BARE_ID = Pattern.compile("^[A-Za-z0-9_-]{10,}$");
    private static final Pattern ENTRY_ID = Pattern.compile("id=\"entry-([A-Za-z0-9_-]+)\"");
    private static final Pattern ENTRY_HREF = Pattern.compile("href=\"([^\"]+)\"");
    private static final Pattern ENTRY_TITLE = Pattern.compile("flip-entry-title\">([^<]*)<");
    private static final String[] IMAGE_TYPES = {".jpg", ".jpeg", ".png", ".webp", ".bmp"};

    public static class Result {
        public final List<File> images;
        public final String message;
        public final boolean ok;
        Result(List<File> images, String message, boolean ok) {
            this.images = images; this.message = message; this.ok = ok;
        }
    }

    private final File imageDir;

    public DriveSync(File imageDir) {
        this.imageDir = imageDir;
        //noinspection ResultOfMethodCallIgnored
        imageDir.mkdirs();
    }

    /** Pulls the folder ID out of a full Drive link, or accepts a bare ID. */
    public static String folderId(String input) {
        if (input == null) return null;
        String s = input.trim();
        Matcher m = FOLDER_IN_URL.matcher(s);
        if (m.find()) return m.group(1);
        m = ID_PARAM.matcher(s);
        if (m.find()) return m.group(1);
        return BARE_ID.matcher(s).matches() ? s : null;
    }

    /** Images already saved on the TV, in playing order. Works offline. */
    public List<File> savedImages() {
        List<File> files = new ArrayList<>();
        File[] all = imageDir.listFiles();
        if (all != null) {
            for (File f : all) if (f.isFile() && !f.getName().endsWith(".part")) files.add(f);
        }
        Collections.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return files;
    }

    /** Downloads new images, removes deleted ones. Runs on a background thread. */
    public Result sync(String folderSetting) {
        String folderId = folderId(folderSetting);
        if (folderId == null) {
            return new Result(savedImages(), "No Google Drive folder set", false);
        }

        List<String[]> entries; // {fileId, name}
        try {
            entries = listFolder(folderId);
        } catch (IOException e) {
            return new Result(savedImages(), "Offline: showing saved images (" + e.getMessage() + ")", false);
        }

        Set<String> keep = new HashSet<>();
        int downloaded = 0;
        int failed = 0;
        for (String[] entry : entries) {
            String fileName = localName(entry[0], entry[1]);
            keep.add(fileName);
            File target = new File(imageDir, fileName);
            if (target.exists() && target.length() > 0) continue;
            try {
                download(entry[0], target);
                downloaded++;
            } catch (IOException e) {
                failed++;
            }
        }

        // An empty list may mean Google changed its page, so never wipe everything.
        if (!entries.isEmpty()) {
            File[] existing = imageDir.listFiles();
            if (existing != null) {
                for (File f : existing) {
                    if (!keep.contains(f.getName())) //noinspection ResultOfMethodCallIgnored
                        f.delete();
                }
            }
        }

        List<File> images = savedImages();
        String message = images.size() + " images. "
                + (downloaded > 0 ? downloaded + " new. " : "")
                + (failed > 0 ? failed + " failed to download. " : "")
                + (entries.isEmpty() ? "Folder looks empty or is not shared publicly." : "Up to date.");
        return new Result(images, message.trim(), failed == 0 && !entries.isEmpty());
    }

    /** Sort key = Drive file name, so staff control the order by naming files. */
    private static String localName(String fileId, String name) {
        String safe = name.replaceAll("[^A-Za-z0-9._ -]", "_");
        return safe + "__" + fileId;
    }

    private List<String[]> listFolder(String folderId) throws IOException {
        return parseFolderPage(new String(get("https://drive.google.com/embeddedfolderview?id=" + folderId), "UTF-8"));
    }

    // shortcut: reads Google's public folder web page, no API key. If Google changes that page, switch to the Drive API.
    /** Returns {fileId, name} for each image in the folder page, in page order. */
    static List<String[]> parseFolderPage(String html) {
        List<String[]> result = new ArrayList<>();
        String[] chunks = html.split("class=\"flip-entry\"");
        for (int i = 1; i < chunks.length; i++) {
            String chunk = chunks[i];
            Matcher id = ENTRY_ID.matcher(chunk);
            Matcher href = ENTRY_HREF.matcher(chunk);
            Matcher title = ENTRY_TITLE.matcher(chunk);
            if (!id.find() || !title.find()) continue;
            if (href.find() && !href.group(1).contains("/file/d/")) continue; // subfolders, Docs
            String name = unescape(title.group(1)).trim();
            if (isImage(name)) result.add(new String[]{id.group(1), name});
        }
        return result;
    }

    private static String unescape(String s) {
        return s.replace("&#39;", "'").replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
    }

    private static boolean isImage(String name) {
        String lower = name.toLowerCase(Locale.US);
        for (String type : IMAGE_TYPES) if (lower.endsWith(type)) return true;
        return false;
    }

    private void download(String fileId, File target) throws IOException {
        byte[] data = get("https://drive.google.com/uc?export=download&id=" + fileId);
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
        if (bounds.outWidth <= 0) throw new IOException("not an image");
        File part = new File(target.getPath() + ".part");
        try (OutputStream out = new FileOutputStream(part)) {
            out.write(data);
        }
        if (!part.renameTo(target)) throw new IOException("could not save");
    }

    private static byte[] get(String address) throws IOException {
        URL url = new URL(address);
        for (int redirects = 0; redirects < 5; redirects++) {
            HttpURLConnection c = (HttpURLConnection) url.openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(60000);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent", USER_AGENT);
            int code = c.getResponseCode();
            if (code >= 300 && code < 400) {
                String location = c.getHeaderField("Location");
                c.disconnect();
                if (location == null) throw new IOException("bad redirect");
                url = new URL(url, location);
                continue;
            }
            if (code != 200) {
                c.disconnect();
                throw new IOException("HTTP " + code);
            }
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[64 * 1024];
                int n;
                while ((n = in.read(chunk)) > 0) buffer.write(chunk, 0, n);
                return buffer.toByteArray();
            } finally {
                c.disconnect();
            }
        }
        throw new IOException("too many redirects");
    }
}
