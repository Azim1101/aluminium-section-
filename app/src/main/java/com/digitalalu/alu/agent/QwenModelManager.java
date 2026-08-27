package com.digitalalu.alu.agent;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Model manager for on-device Qwen 0.5B / 0.6B LLM (under 500MB).
 *
 * Supports:
 * 1. Model status check (size, path, readiness)
 * 2. In-app background downloader with live progress updates
 * 3. Local file import (.onnx / .bin / .gguf)
 * 4. Model deletion to free device storage
 */
public class QwenModelManager {

    private static final String TAG = "QwenModelManager";
    public static final String MODEL_NAME = "Qwen 2.5 0.5B Instruct";
    public static final String MODEL_FILE_NAME = "qwen2.5-0.5b-instruct-int4.onnx";
    public static final String MODEL_SUBDIR = "models/qwen";

    // Primary download link for mobile-quantized Qwen 2.5 0.5B (under 500MB)
    public static final String DEFAULT_DOWNLOAD_URL =
            "https://huggingface.co/onnx-community/Qwen2.5-0.5B-Instruct/resolve/main/onnx/model_quantized.onnx";

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Thread downloadThread;
    private volatile boolean isCancelled = false;

    public interface DownloadListener {
        void onProgress(int percent, long bytesDownloaded, long totalBytes);
        void onSuccess(File modelFile);
        void onError(String message);
    }

    public QwenModelManager(Context context) {
        this.context = context.getApplicationContext();
    }

    /** Directory where Qwen model is stored on internal app storage */
    public File getModelDirectory() {
        File dir = new File(context.getFilesDir(), MODEL_SUBDIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /** Path to Qwen model file */
    public File getModelFile() {
        return new File(getModelDirectory(), MODEL_FILE_NAME);
    }

    /** Checks whether Qwen model exists and is ready for inference */
    public boolean isModelDownloaded() {
        File f = getModelFile();
        return f.exists() && f.isFile() && f.length() > 10 * 1024 * 1024; // > 10MB
    }

    /** Formatted size of the installed model */
    public String getFormattedModelSize() {
        File f = getModelFile();
        if (!f.exists()) return "Not downloaded";
        long bytes = f.length();
        if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    /** Start downloading Qwen 0.5B in the background */
    public void startDownload(DownloadListener listener) {
        if (isDownloading()) {
            if (listener != null) listener.onError("Download is already running");
            return;
        }

        isCancelled = false;
        downloadThread = new Thread(() -> {
            File targetFile = getModelFile();
            File tempFile = new File(targetFile.getParentFile(), MODEL_FILE_NAME + ".download");

            HttpURLConnection conn = null;
            InputStream in = null;
            FileOutputStream out = null;

            try {
                URL url = new URL(DEFAULT_DOWNLOAD_URL);
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(25000);
                conn.setReadTimeout(30000);
                conn.setInstanceFollowRedirects(true);
                conn.setRequestProperty("User-Agent", "ALU-Window-Android-Agent");

                int responseCode = conn.getResponseCode();
                // Handle redirect if any
                if (responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                    responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                    responseCode == 307 || responseCode == 308) {
                    String newUrl = conn.getHeaderField("Location");
                    conn.disconnect();
                    url = new URL(newUrl);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(25000);
                    conn.setReadTimeout(30000);
                    conn.setRequestProperty("User-Agent", "ALU-Window-Android-Agent");
                    responseCode = conn.getResponseCode();
                }

                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw new Exception("HTTP server returned: " + responseCode + " " + conn.getResponseMessage());
                }

                long totalBytes = conn.getContentLengthLong();
                if (totalBytes <= 0) totalBytes = 350L * 1024 * 1024; // estimate ~350MB

                in = conn.getInputStream();
                out = new FileOutputStream(tempFile);

                byte[] buffer = new byte[32768];
                long bytesDownloaded = 0;
                int read;
                long lastProgressTime = 0;

                while ((read = in.read(buffer)) != -1) {
                    if (isCancelled) {
                        tempFile.delete();
                        return;
                    }
                    out.write(buffer, 0, read);
                    bytesDownloaded += read;

                    long now = System.currentTimeMillis();
                    if (now - lastProgressTime > 250) {
                        lastProgressTime = now;
                        final int percent = (int) ((bytesDownloaded * 100) / totalBytes);
                        final long dl = bytesDownloaded;
                        final long tot = totalBytes;
                        mainHandler.post(() -> {
                            if (listener != null) listener.onProgress(percent, dl, tot);
                        });
                    }
                }

                out.flush();
                out.close();
                out = null;

                // Rename tempFile to targetFile
                if (targetFile.exists()) targetFile.delete();
                if (!tempFile.renameTo(targetFile)) {
                    throw new Exception("Failed to finalize downloaded model file");
                }

                mainHandler.post(() -> {
                    if (listener != null) listener.onSuccess(targetFile);
                });

            } catch (Exception e) {
                Log.e(TAG, "Download error: " + e.getMessage(), e);
                if (tempFile.exists()) tempFile.delete();
                final String msg = e.getMessage();
                mainHandler.post(() -> {
                    if (listener != null) listener.onError("Download failed: " + msg);
                });
            } finally {
                try { if (in != null) in.close(); } catch (Exception ignored) {}
                try { if (out != null) out.close(); } catch (Exception ignored) {}
                if (conn != null) conn.disconnect();
                downloadThread = null;
            }
        });

        downloadThread.start();
    }

    /** Cancel ongoing download */
    public void cancelDownload() {
        isCancelled = true;
        if (downloadThread != null) {
            downloadThread.interrupt();
            downloadThread = null;
        }
    }

    public boolean isDownloading() {
        return downloadThread != null && downloadThread.isAlive();
    }

    /** Import an existing model file selected by the user */
    public boolean importModel(Uri sourceUri) {
        try {
            InputStream in = context.getContentResolver().openInputStream(sourceUri);
            if (in == null) return false;
            File targetFile = getModelFile();
            FileOutputStream out = new FileOutputStream(targetFile);
            byte[] buf = new byte[32768];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            out.flush();
            out.close();
            in.close();
            return isModelDownloaded();
        } catch (Exception e) {
            Log.e(TAG, "Import error: " + e.getMessage(), e);
            return false;
        }
    }

    /** Delete model to free disk space */
    public boolean deleteModel() {
        File f = getModelFile();
        if (f.exists()) {
            return f.delete();
        }
        return false;
    }
}
