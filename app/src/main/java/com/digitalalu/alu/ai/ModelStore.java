package com.digitalalu.alu.ai;

import android.app.DownloadManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Downloads and tracks MNN model files using the system DownloadManager
 * (resumable, notification, survives app restarts).
 *
 * Files live in <externalFilesDir>/models/ — app-private, no storage permission.
 * Download ids are persisted so progress keeps working after the app is killed.
 */
public class ModelStore {

    private static final String DL_PREFS = "alu_ai_dl";

    public static File modelsDir(Context c) {
        File base = c.getExternalFilesDir(null);
        if (base == null) base = c.getFilesDir();
        File dir = new File(base, "models");
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return dir;
    }

    /** Each model lives in its own folder: models/<id>/ (MNN = several files). */
    public static File dirFor(Context c, ModelCatalog.ModelSpec m) {
        File d = new File(modelsDir(c), m.id);
        if (!d.exists()) d.mkdirs();
        return d;
    }

    /** Entry point handed to the native loader (config.json for MNN). */
    public static File fileFor(Context c, ModelCatalog.ModelSpec m) {
        return new File(dirFor(c, m), m.fileName);
    }

    public static File fileFor(Context c, ModelCatalog.ModelSpec m, ModelCatalog.ModelFile f) {
        return new File(dirFor(c, m), f.name);
    }

    /** Small json files must match closely; big weights get the usual 2% slack. */
    static boolean fileOk(File f, long expected) {
        if (!f.exists()) return false;
        long len = f.length();
        if (expected <= 0) return len > 0;
        if (expected < 1024L * 1024L) return len > 0 && Math.abs(len - expected) <= Math.max(64L, expected / 10);
        return sizeMatches(len, expected);
    }

    /* ---------------------- install detection ------------------------- */

    public static boolean sizeMatches(long actual, long expected) {
        if (actual <= 0 || expected <= 0) return false;
        double ratio = (double) actual / (double) expected;
        return ratio > 0.98 && ratio < 1.02;
    }

    public static boolean isInstalled(Context c, ModelCatalog.ModelSpec m) {
        try {
            if (m.files == null || m.files.length == 0) {
                File f = fileFor(c, m);
                return f.exists() && sizeMatches(f.length(), m.sizeBytes);
            }
            for (ModelCatalog.ModelFile mf : m.files) {
                if (!fileOk(fileFor(c, m, mf), mf.sizeBytes)) return false;
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Which file is still missing / incomplete ("" when all good). */
    public static String missingFile(Context c, ModelCatalog.ModelSpec m) {
        try {
            for (ModelCatalog.ModelFile mf : m.files) {
                if (!fileOk(fileFor(c, m, mf), mf.sizeBytes)) return mf.name;
            }
        } catch (Throwable ignored) { }
        return "";
    }

    public static long bytesPresent(Context c, ModelCatalog.ModelSpec m) {
        try {
            long sum = 0L;
            if (m.files == null || m.files.length == 0) {
                File f = fileFor(c, m);
                return f.exists() ? f.length() : 0L;
            }
            for (ModelCatalog.ModelFile mf : m.files) {
                File f = fileFor(c, m, mf);
                if (f.exists()) sum += f.length();
                else {
                    File part = new File(f.getPath() + ".part");
                    if (part.exists()) sum += Math.min(part.length(), mf.sizeBytes);
                }
            }
            return sum;
        } catch (Throwable t) {
            return 0L;
        }
    }

    /**
     * Safety check before handing a file to the native loader: exact-ish size AND
     * the expected byte size. Loading a truncated (or random) file is the #1 way to get a
     * native crash, so we refuse early and say why in plain Hinglish.
     */
    public static String verify(Context c, ModelCatalog.ModelSpec m) {
        try {
            if (m.files != null && m.files.length > 0) {
                String miss = missingFile(c, m);
                if (miss.length() > 0) {
                    return "model file adhuri/missing: " + miss + " ("
                            + humanBytes(bytesPresent(c, m)) + " / " + humanBytes(m.sizeBytes) + ")";
                }
                return "";
            }
            File f = fileFor(c, m);
            if (!f.exists()) return "model file mili nahi";
            long len = f.length();
            if (len <= 0) return "model file khali hai";
            if (m.sizeBytes > 0 && !sizeMatches(len, m.sizeBytes)) {
                return "download adhura hai (" + humanBytes(len) + " / " + humanBytes(m.sizeBytes) + ")";
            }
            return "";
        } catch (Throwable t) {
            return "file check fail: " + t.getClass().getSimpleName();
        }
    }

    public static List<ModelCatalog.ModelSpec> installed(Context c) {
        List<ModelCatalog.ModelSpec> out = new ArrayList<ModelCatalog.ModelSpec>();
        for (ModelCatalog.ModelSpec m : ModelCatalog.list()) if (isInstalled(c, m)) out.add(m);
        return out;
    }

    public static ModelCatalog.ModelSpec bestInstalled(Context c, long totalRam) {
        ModelCatalog.ModelSpec best = null;
        for (ModelCatalog.ModelSpec m : installed(c)) {
            if (totalRam > 0 && totalRam < m.minRamBytes) continue;
            if (best == null || m.minRamBytes > best.minRamBytes) best = m;
        }
        return best;
    }

    /** Single-model app — remove old GGUF-era files / other model folders to free space. */
    public static void deleteOthers(Context c) {
        try {
            File dir = modelsDir(c);
            File[] fs = dir.listFiles();
            if (fs == null) return;
            for (File f : fs) {
                if (f.isDirectory() && ModelCatalog.byId(f.getName()) != null) continue;
                rmrf(f);
            }
        } catch (Throwable ignored) { }
    }

    static void rmrf(File f) {
        try {
            if (f.isDirectory()) {
                File[] kids = f.listFiles();
                if (kids != null) for (File k : kids) rmrf(k);
            }
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        } catch (Throwable ignored) { }
    }

    public static void delete(Context c, ModelCatalog.ModelSpec m) {
        try {
            cancelModel(c, m);
            rmrf(dirFor(c, m));
        } catch (Throwable ignored) { }
    }

    /* --------------------------- downloads ---------------------------- */

    /** @return first DownloadManager id, -1 on failure, -2 when already complete. */
    /** Synthetic download id used for the in-app downloader (any value > 0 works for the UI). */
    public static final long IN_APP_ID = 1L;

    /**
     * Starts the in-app foreground downloader (v2.8.1). The system DownloadManager
     * was the primary path before, but it is disabled on many phones and threw on
     * enqueue — so now it is not used at all.
     * @return IN_APP_ID when started, -2 when already installed, -1 on failure.
     */
    public static long enqueue(Context c, ModelCatalog.ModelSpec m, boolean wifiOnly) {
        try {
            if (isInstalled(c, m)) return -2;
            // leftover partial files from the old DownloadManager path confuse nobody now:
            // the in-app downloader resumes *.part files and re-checks sizes itself.
            ModelDownloadService.start(c, m);
            remember(c, m.id, IN_APP_ID);
            return IN_APP_ID;
        } catch (Throwable t) {
            return -1;
        }
    }

    public static long[] downloadIdsFor(Context c, String modelId) {
        try {
            String v = c.getApplicationContext().getSharedPreferences(DL_PREFS, Context.MODE_PRIVATE)
                    .getString("ids_" + modelId, "");
            if (v == null || v.length() == 0) return new long[0];
            String[] parts = v.split(",");
            long[] out = new long[parts.length];
            for (int i = 0; i < parts.length; i++) out[i] = Long.parseLong(parts[i].trim());
            return out;
        } catch (Throwable t) {
            return new long[0];
        }
    }

    public static boolean cancel(Context c, long downloadId) {
        if (downloadId <= 0) return false;
        try {
            DownloadManager dm = (DownloadManager) c.getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm == null) return false;
            dm.remove(downloadId);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void cancelModel(Context c, ModelCatalog.ModelSpec m) {
        try { ModelDownloadService.cancel(c); } catch (Throwable ignored) { }
        try {
            for (long id : downloadIdsFor(c, m.id)) cancel(c, id);
            long one = downloadIdFor(c, m.id);
            if (one > 0) cancel(c, one);
        } catch (Throwable ignored) { }
        forget(c, m.id);
    }

    /* ------- download-id persistence (survives app restarts) ---------- */

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(DL_PREFS, Context.MODE_PRIVATE);
    }

    public static void remember(Context c, String modelId, long downloadId) {
        try {
            prefs(c).edit().putLong("dl_" + modelId, downloadId).apply();
        } catch (Throwable ignored) { }
    }

    public static long downloadIdFor(Context c, String modelId) {
        try {
            return prefs(c).getLong("dl_" + modelId, -1L);
        } catch (Throwable t) {
            return -1L;
        }
    }

    public static void forget(Context c, String modelId) {
        try {
            c.getApplicationContext().getSharedPreferences(DL_PREFS, Context.MODE_PRIVATE)
                    .edit().remove("ids_" + modelId).apply();
        } catch (Throwable ignored) { }
        try {
            prefs(c).edit().remove("dl_" + modelId).apply();
        } catch (Throwable ignored) { }
    }

    /* ---------------------------- progress ---------------------------- */

    public static class Progress {
        public int percent;
        public long bytes;
        public long total;
        public boolean done;
        public boolean failed;
        public boolean active;
        public long bytesPerSec;
        public String error = "";

        public String percentText() { return percent + "%"; }

        public String detailText() {
            StringBuilder sb = new StringBuilder();
            sb.append(humanBytes(bytes)).append(" / ").append(humanBytes(total > 0 ? total : 0));
            if (active && bytesPerSec > 0) {
                sb.append("  •  ").append(humanBytes(bytesPerSec)).append("/s");
                long eta = etaSeconds(bytesPerSec, total - bytes);
                if (eta > 0) sb.append("  •  ").append(etaText(eta)).append(" bacha");
            }
            return sb.toString();
        }

        public String statusText() {
            if (done) return "download complete ✔";
            if (failed) return "download fail" + (error != null && error.length() > 0 ? " (" + error + ")" : "") + " — dobara try karein";
            if (bytes > 0) return detailText();
            return "download shuru ho raha hai…";
        }
    }

    public static Progress progress(Context c, long downloadId, ModelCatalog.ModelSpec m) {
        Progress p = new Progress();
        p.total = m.sizeBytes;
        ModelDownloadService.State dls = ModelDownloadService.state(m.id);
        if (dls != null) {
            p.bytes = Math.max(dls.bytes, bytesPresent(c, m));
            p.active = dls.running;
            p.done = dls.done || isInstalled(c, m);
            p.failed = !p.done && (dls.failed || dls.cancelled || !dls.running);
            p.bytesPerSec = dls.bytesPerSec;
            p.error = dls.error;
            p.percent = p.total > 0 ? (int) Math.min(100, (p.bytes * 100L) / p.total) : 0;
            return p;
        }
        if (downloadId == IN_APP_ID) {
            // the process was killed mid-download: partial files are on disk, nothing running
            p.bytes = bytesPresent(c, m);
            p.done = isInstalled(c, m);
            p.failed = !p.done;
            p.percent = p.total > 0 ? (int) Math.min(100, (p.bytes * 100L) / p.total) : 0;
            return p;
        }
        long[] ids = downloadIdsFor(c, m.id);
        if (ids.length == 0 && downloadId > 0) ids = new long[]{downloadId};
        long down = 0L;
        boolean anyActive = false, anyFailed = false, allDone = ids.length > 0;
        try {
            DownloadManager dm = (DownloadManager) c.getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm != null) {
                for (long id : ids) {
                    Cursor cur = dm.query(new DownloadManager.Query().setFilterById(id));
                    if (cur == null) { allDone = false; continue; }
                    try {
                        if (cur.moveToFirst()) {
                            int iBytes = cur.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                            int iStat = cur.getColumnIndex(DownloadManager.COLUMN_STATUS);
                            down += iBytes >= 0 ? cur.getLong(iBytes) : 0L;
                            int st = iStat >= 0 ? cur.getInt(iStat) : 0;
                            if (st == DownloadManager.STATUS_RUNNING || st == DownloadManager.STATUS_PENDING
                                    || st == DownloadManager.STATUS_PAUSED) anyActive = true;
                            if (st == DownloadManager.STATUS_FAILED) anyFailed = true;
                            if (st != DownloadManager.STATUS_SUCCESSFUL) allDone = false;
                        } else {
                            allDone = false;   // row vanished (user cleared it)
                        }
                    } finally {
                        cur.close();
                    }
                }
            }
        } catch (Throwable ignored) { }

        // files already complete on disk count too
        long onDisk = bytesPresent(c, m);
        p.bytes = Math.max(down, onDisk);
        p.active = anyActive;
        p.failed = anyFailed;
        p.done = allDone || isInstalled(c, m);
        if (!p.active && !p.done && !p.failed && ids.length > 0) p.failed = true;
        if (p.total <= 0) p.total = m.sizeBytes;
        p.percent = p.total > 0 ? (int) Math.min(100, (p.bytes * 100L) / p.total) : 0;
        return p;
    }

    public static boolean storageOk(Context c, ModelCatalog.ModelSpec m) {
        try {
            File dir = modelsDir(c);
            return dir.getUsableSpace() > (long) (m.sizeBytes * 1.2);
        } catch (Throwable t) {
            return true;
        }
    }

    /* ---------------------------- formatting -------------------------- */

    public static String humanBytes(long b) {
        if (b < 0) b = 0;
        if (b < 1024L) return b + " B";
        double kb = b / 1024.0;
        if (kb < 1024) return String.format(Locale.US, "%.0f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(Locale.US, "%.0f MB", mb);
        return String.format(Locale.US, "%.2f GB", mb / 1024.0);
    }

    public static long etaSeconds(long bytesPerSec, long remainingBytes) {
        if (bytesPerSec <= 0 || remainingBytes <= 0) return 0L;
        return remainingBytes / bytesPerSec;
    }

    public static String etaText(long seconds) {
        if (seconds <= 0) return "";
        if (seconds < 60) return seconds + " sec";
        long m = seconds / 60;
        if (m < 60) return m + " min";
        long h = m / 60;
        return h + " ghante " + (m % 60) + " min";
    }

    /** Smoothed download speed from two samples. Pure — unit tested. */
    public static long speed(long prevBytes, long prevTimeMs, long nowBytes, long nowTimeMs, long prevSpeed) {
        long dt = nowTimeMs - prevTimeMs;
        long db = nowBytes - prevBytes;
        if (dt <= 0 || db < 0) return prevSpeed;
        long inst = (db * 1000L) / dt;
        if (prevSpeed <= 0) return inst;
        return (long) (prevSpeed * 0.65 + inst * 0.35);
    }
}
