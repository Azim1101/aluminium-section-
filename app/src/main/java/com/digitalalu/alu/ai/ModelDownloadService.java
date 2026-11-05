package com.digitalalu.alu.ai;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * In-app model downloader (v2.8.1).
 *
 * The system DownloadManager is disabled / crippled on many Indian phones
 * (MIUI, Realme UI, Vivo "Download Manager" app turned off, parental tools) and
 * then {@code dm.enqueue()} throws — the user saw "Download shuru nahi hua".
 * This foreground service downloads the model files itself with plain
 * HttpURLConnection: sequential files, HTTP Range resume of {@code .part}
 * files, a progress notification, and a static {@link State} the chat screen
 * polls once a second.
 */
public class ModelDownloadService extends Service {

    public static final String EXTRA_MODEL = "model";
    public static final String ACTION_CANCEL = "com.digitalalu.alu.DL_CANCEL";
    private static final String CHANNEL = "alu_model_dl";
    private static final int NOTIF_ID = 4411;

    /** Snapshot of the running / last download; null when nothing happened yet. */
    public static class State {
        public String modelId;
        public volatile boolean running, done, failed, cancelled;
        public volatile long bytes, total, bytesPerSec;
        public volatile String error = "", currentFile = "";
    }

    private static volatile State sState;
    private static volatile Thread sWorker;

    public static State state(String modelId) {
        State s = sState;
        if (s == null || modelId == null || !modelId.equals(s.modelId)) return null;
        return s;
    }

    public static boolean isRunning() {
        State s = sState;
        return s != null && s.running;
    }

    public static void start(Context c, ModelCatalog.ModelSpec m) {
        Intent i = new Intent(c, ModelDownloadService.class).putExtra(EXTRA_MODEL, m.id);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
    }

    public static void cancel(Context c) {
        State s = sState;
        if (s != null) { s.cancelled = true; }
        Thread t = sWorker;
        if (t != null) t.interrupt();
        try { c.stopService(new Intent(c, ModelDownloadService.class)); } catch (Throwable ignored) { }
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String id = intent != null ? intent.getStringExtra(EXTRA_MODEL) : null;
        if (intent != null && ACTION_CANCEL.equals(intent.getAction())) {
            cancel(this);
            stopSelf();
            return START_NOT_STICKY;
        }
        final ModelCatalog.ModelSpec m = id != null ? ModelCatalog.byId(id) : ModelCatalog.DEFAULT();
        if (m == null) { stopSelf(); return START_NOT_STICKY; }
        startInForeground(m, "shuru ho raha hai…", 0);
        if (isRunning()) return START_STICKY;      // already downloading this model

        final State st = new State();
        st.modelId = m.id;
        st.total = m.sizeBytes;
        st.running = true;
        sState = st;
        Thread t = new Thread(() -> run(m, st), "alu-model-dl");
        sWorker = t;
        t.start();
        return START_STICKY;
    }

    private void run(ModelCatalog.ModelSpec m, State st) {
        PowerManager.WakeLock wl = null;
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "alu:modeldl");
                wl.acquire(3 * 60 * 60 * 1000L);
            }
            File dir = ModelStore.dirFor(this, m);
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            long doneBefore = 0;
            for (ModelCatalog.ModelFile mf : m.files) {
                if (st.cancelled) break;
                File target = new File(dir, mf.name);
                if (ModelStore.fileOk(target, mf.sizeBytes)) { doneBefore += mf.sizeBytes; continue; }
                st.currentFile = mf.name;
                downloadOne(m.urlFor(mf), target, mf.sizeBytes, st, doneBefore, m);
                doneBefore += mf.sizeBytes;
            }
            if (!st.cancelled) {
                st.done = ModelStore.isInstalled(this, m);
                if (!st.done) { st.failed = true; if (st.error.length() == 0) st.error = "file size mismatch"; }
            }
        } catch (InterruptedException ie) {
            st.cancelled = true;
        } catch (Throwable t) {
            st.failed = true;
            st.error = t.getClass().getSimpleName() + (t.getMessage() != null ? ": " + t.getMessage() : "");
        } finally {
            st.running = false;
            sWorker = null;
            if (wl != null) try { wl.release(); } catch (Throwable ignored) { }
            try {
                if (st.done) notifyFinal("✅ AI model download complete", "ALU Window ab offline AI ke saath taiyaar hai");
                else if (st.failed) notifyFinal("❌ AI model download fail", st.error + " — app kholke dobara try karo (jahan ruka wahin se chalega)");
                else { NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE); if (nm != null) nm.cancel(NOTIF_ID); }
            } catch (Throwable ignored) { }
            stopForegroundCompat();
            stopSelf();
        }
    }

    /** One file with up to 6 resume attempts; writes to name.part, renames when size matches. */
    private void downloadOne(String url, File target, long expected, State st, long doneBefore,
                             ModelCatalog.ModelSpec m) throws Exception {
        File part = new File(target.getPath() + ".part");
        Exception last = null;
        for (int attempt = 0; attempt < 6 && !st.cancelled; attempt++) {
            long have = part.exists() ? part.length() : 0;
            if (have > expected) { //noinspection ResultOfMethodCallIgnored
                part.delete(); have = 0; }
            if (have == expected) break;
            HttpURLConnection conn = null;
            try {
                conn = open(url, have);
                int code = conn.getResponseCode();
                if (code == 416) { //noinspection ResultOfMethodCallIgnored
                    part.delete(); have = 0; continue; }
                if (code != 200 && code != 206) throw new java.io.IOException("HTTP " + code);
                boolean append = code == 206 && have > 0;
                if (!append) have = 0;
                try (InputStream in = conn.getInputStream();
                     FileOutputStream out = new FileOutputStream(part, append)) {
                    byte[] buf = new byte[256 * 1024];
                    long lastT = System.currentTimeMillis(), lastB = have, lastNotif = 0;
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        if (st.cancelled || Thread.currentThread().isInterrupted()) throw new InterruptedException();
                        out.write(buf, 0, n);
                        have += n;
                        st.bytes = doneBefore + have;
                        long now = System.currentTimeMillis();
                        if (now - lastT >= 1000) {
                            st.bytesPerSec = (have - lastB) * 1000L / Math.max(1, now - lastT);
                            lastT = now; lastB = have;
                        }
                        if (now - lastNotif >= 2000) {
                            lastNotif = now;
                            int pct = st.total > 0 ? (int) (st.bytes * 100 / st.total) : 0;
                            startInForeground(m, pct + "%  •  " + ModelStore.humanBytes(st.bytes) + " / "
                                    + ModelStore.humanBytes(st.total)
                                    + (st.bytesPerSec > 0 ? "  •  " + ModelStore.humanBytes(st.bytesPerSec) + "/s" : ""), pct);
                        }
                    }
                    out.getFD().sync();
                }
                if (part.length() == expected) {
                    if (target.exists()) //noinspection ResultOfMethodCallIgnored
                        target.delete();
                    if (!part.renameTo(target)) throw new java.io.IOException("rename failed");
                    return;
                }
                last = new java.io.IOException("short file " + part.length() + "/" + expected);
            } catch (InterruptedException ie) {
                throw ie;
            } catch (Exception e) {
                last = e;
                st.error = e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : "");
                Thread.sleep(1500L * (attempt + 1));      // network hiccup → back off, resume
            } finally {
                if (conn != null) try { conn.disconnect(); } catch (Throwable ignored) { }
            }
        }
        if (st.cancelled) throw new InterruptedException();
        if (part.exists() && part.length() == expected) {
            if (!part.renameTo(target)) throw new java.io.IOException("rename failed");
            return;
        }
        throw last != null ? last : new java.io.IOException("download failed");
    }

    private static HttpURLConnection open(String url, long from) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setInstanceFollowRedirects(true);
        c.setConnectTimeout(20000);
        c.setReadTimeout(30000);
        c.setRequestProperty("User-Agent", "ALU-Window-Android");
        if (from > 0) c.setRequestProperty("Range", "bytes=" + from + "-");
        // HuggingFace resolves to a CDN on another host: follow manually too (Java drops
        // cross-host redirects only when the scheme changes, but be safe).
        c.connect();
        int code = c.getResponseCode();
        if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
            String loc = c.getHeaderField("Location");
            c.disconnect();
            if (loc != null) return open(loc.startsWith("http") ? loc : new URL(new URL(url), loc).toString(), from);
        }
        return c;
    }

    /* ------------------------------ notification ------------------------------ */

    private void startInForeground(ModelCatalog.ModelSpec m, String text, int pct) {
        try {
            Notification n = build("⬇ AI model download — " + m.title, text, pct, true);
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(NOTIF_ID, n);
            }
        } catch (Throwable t) {
            // Without a notification the service still runs while the app is alive.
        }
    }

    private void notifyFinal(String title, String text) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIF_ID + 1, build(title, text, -1, false));
    }

    private Notification build(String title, String text, int pct, boolean ongoing) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm != null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "AI model download",
                    NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        Intent open = getPackageManager().getLaunchIntentForPackage(getPackageName());
        PendingIntent pi = open == null ? null : PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        b.setContentTitle(title).setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text))
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setOngoing(ongoing).setOnlyAlertOnce(true);
        if (pi != null) b.setContentIntent(pi);
        if (pct >= 0) b.setProgress(100, pct, pct == 0);
        if (ongoing) {
            Intent ci = new Intent(this, ModelDownloadService.class).setAction(ACTION_CANCEL);
            PendingIntent cpi = PendingIntent.getService(this, 1, ci,
                    PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
            b.addAction(new Notification.Action.Builder(null, "Cancel", cpi).build());
        }
        return b.build();
    }

    private void stopForegroundCompat() {
        try {
            if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE); else stopForeground(true);
        } catch (Throwable ignored) { }
    }
}
