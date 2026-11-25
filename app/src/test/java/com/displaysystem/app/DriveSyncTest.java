package com.displaysystem.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.List;
import org.junit.Test;

public class DriveSyncTest {
    @Test
    public void readsFolderIdFromAnyLinkStyle() {
        String id = "1AbCdEfGhIjKlMnOpQ_rs-TuV";
        assertEquals(id, DriveSync.folderId("https://drive.google.com/drive/folders/" + id + "?usp=sharing"));
        assertEquals(id, DriveSync.folderId("https://drive.google.com/drive/u/0/folders/" + id));
        assertEquals(id, DriveSync.folderId("https://drive.google.com/open?id=" + id));
        assertEquals(id, DriveSync.folderId("  " + id + " "));
        assertNull(DriveSync.folderId("not a link"));
        assertNull(DriveSync.folderId(""));
    }

    @Test
    public void keepsOnlyImageFilesFromFolderPage() {
        String html = "<div class=\"flip-entries\">"
                + entry("IMG1aaaaaaaa", "https://drive.google.com/file/d/IMG1aaaaaaaa/view?usp=drive_web", "02 Latte &amp; Cake.jpg")
                + entry("SUBfolder123", "https://drive.google.com/drive/folders/SUBfolder123", "Old menus.png")
                + entry("DOC111111111", "https://docs.google.com/document/d/DOC111111111/edit", "Notes")
                + entry("IMG2bbbbbbbb", "https://drive.google.com/file/d/IMG2bbbbbbbb/view?usp=drive_web", "01 Menu.PNG")
                + entry("PDF333333333", "https://drive.google.com/file/d/PDF333333333/view?usp=drive_web", "prices.pdf")
                + "</div>";
        List<String[]> images = DriveSync.parseFolderPage(html);
        assertEquals(2, images.size());
        assertEquals("IMG1aaaaaaaa", images.get(0)[0]);
        assertEquals("02 Latte & Cake.jpg", images.get(0)[1]);
        assertEquals("IMG2bbbbbbbb", images.get(1)[0]);
    }

    @Test
    public void settingsStepWrapsAround() {
        assertEquals(270, Settings.step(Settings.ROTATIONS, 90, 1));
        assertEquals(180, Settings.step(Settings.ROTATIONS, 90, -1));
        assertEquals(300, Settings.step(Settings.SECONDS_STEPS, 0, -1));
        assertEquals(3, Settings.step(Settings.SECONDS_STEPS, 0, 1));
    }

    private static String entry(String id, String href, String title) {
        return "<div class=\"flip-entry\" id=\"entry-" + id + "\" tabindex=\"0\" role=\"link\">"
                + "<div class=\"flip-entry-info\"><a href=\"" + href + "\" target=\"_blank\">"
                + "<div class=\"flip-entry-thumb\"></div><div class=\"flip-entry-title\">" + title + "</div>"
                + "</a></div></div>";
    }
}
