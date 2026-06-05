package com.vypeensoft.smsmanager;

import android.content.Context;
import android.os.Environment;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

public class SettingsManager {
    private static final String SETTINGS_DIR = "/Vypeensoft/SMS_Manager/settings";
    private static final String SETTINGS_FILE = "settings.json";
    private static final String DEFAULT_EXPORT_DIR = "/Vypeensoft/SMS_Manager/export/";

    public static File getSettingsFile() {
        File dir = new File(Environment.getExternalStorageDirectory(), SETTINGS_DIR);
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, SETTINGS_FILE);
    }

    public static String getDefaultExportPath() {
        File dir = new File(Environment.getExternalStorageDirectory(), DEFAULT_EXPORT_DIR);
        if (!dir.exists()) dir.mkdirs();
        return dir.getAbsolutePath();
    }

    public static JSONObject loadSettings(Context context) {
        File file = getSettingsFile();
        if (!file.exists()) {
            JSONObject defaultSettings = createDefaultSettings();
            saveSettings(context, defaultSettings);
            return defaultSettings;
        }

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            return new JSONObject(new String(data, StandardCharsets.UTF_8));
        } catch (Exception e) {
            e.printStackTrace();
            return createDefaultSettings();
        }
    }

    public static void saveSettings(Context context, JSONObject settings) {
        File file = getSettingsFile();
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(settings.toString(4).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static JSONObject createDefaultSettings() {
        JSONObject settings = new JSONObject();
        try {
            settings.put("font_size", 16);
            settings.put("confirm_delete", true);
            settings.put("sort_ascending", true);
            settings.put("preview_lines", 3);
            settings.put("export_path", getDefaultExportPath());
            settings.put("export_format_xml", true);
            settings.put("export_format_json", true);
            settings.put("export_format_csv", true);
            settings.put("notification_audio", true);
            settings.put("notification_visual", true);
            settings.put("notification_preview", true);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return settings;
    }

    // Helper methods for easy access
    public static int getFontSize(Context context) {
        return loadSettings(context).optInt("font_size", 16);
    }

    public static boolean isConfirmDelete(Context context) {
        return loadSettings(context).optBoolean("confirm_delete", true);
    }

    public static boolean isSortAscending(Context context) {
        return loadSettings(context).optBoolean("sort_ascending", true);
    }

    public static int getPreviewLines(Context context) {
        return loadSettings(context).optInt("preview_lines", 3);
    }

    public static String getExportPath(Context context) {
        return loadSettings(context).optString("export_path", getDefaultExportPath());
    }

    public static boolean isExportXml(Context context) {
        return loadSettings(context).optBoolean("export_format_xml", true);
    }

    public static boolean isExportJson(Context context) {
        return loadSettings(context).optBoolean("export_format_json", true);
    }

    public static boolean isExportCsv(Context context) {
        return loadSettings(context).optBoolean("export_format_csv", true);
    }

    public static boolean isNotificationAudio(Context context) {
        return loadSettings(context).optBoolean("notification_audio", true);
    }

    public static boolean isNotificationVisual(Context context) {
        return loadSettings(context).optBoolean("notification_visual", true);
    }

    public static boolean isNotificationPreview(Context context) {
        return loadSettings(context).optBoolean("notification_preview", true);
    }
}
