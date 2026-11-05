package com.digitalalu.alu.ai;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.StatFs;

import java.io.File;
import java.util.Locale;

/**
 * Probes the device and decides which on-device model tier it can run.
 *
 *   TINY    < 2.5 GB   -> 0.6B class models
 *   SMALL   2.5 - 4 GB -> 1.7B Q2_K / 0.6B
 *   MEDIUM  4 - 6 GB   -> 1.7B Q4_K_M comfortably
 *   LARGE   > 6 GB     -> 4B Q4_K_M (best Hindi quality)
 */
public class DeviceProfile {

    public static final String TIER_TINY = "TINY";
    public static final String TIER_SMALL = "SMALL";
    public static final String TIER_MEDIUM = "MEDIUM";
    public static final String TIER_LARGE = "LARGE";

    public long totalRam;
    public long availRam;
    public long freeStorage;
    public int cores;
    public String abi = "";
    public int sdk;
    public boolean lowRamDevice;
    public String tier = TIER_TINY;
    public boolean runtimeSupported;
    public String recommendation = "";

    public static DeviceProfile probe(Context c) {
        DeviceProfile d = new DeviceProfile();
        try {
            ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            if (am != null) {
                am.getMemoryInfo(mi);
                d.totalRam = mi.totalMem;
                d.availRam = mi.availMem;
                d.lowRamDevice = mi.lowMemory;
            }
        } catch (Throwable ignored) { }

        d.cores = Runtime.getRuntime().availableProcessors();
        d.sdk = Build.VERSION.SDK_INT;
        try {
            String[] abis = Build.SUPPORTED_ABIS;
            d.abi = (abis != null && abis.length > 0) ? abis[0]
                    : (Build.CPU_ABI == null ? "" : Build.CPU_ABI);
        } catch (Throwable t) {
            d.abi = "";
        }
        try {
            File dir = c.getFilesDir();
            StatFs sf = new StatFs(dir.getAbsolutePath());
            d.freeStorage = sf.getAvailableBlocksLong() * sf.getBlockSizeLong();
        } catch (Throwable ignored) { }

        d.tier = tierFor(d.totalRam, d.cores);
        d.runtimeSupported = LocalLlm.available();
        d.recommendation = recommendText(d);
        return d;
    }

    /** Pure function so unit tests can exercise it without Android. */
    public static String tierFor(long totalRam, int cores) {
        double gb = totalRam / 1073741824.0;
        if (totalRam <= 0) return TIER_SMALL;
        if (gb < 2.5) return TIER_TINY;
        if (gb < 4.0) return TIER_SMALL;
        if (gb < 6.0) return TIER_MEDIUM;
        return TIER_LARGE;
    }

    /** Rough tokens/sec guess for a Q4_K_M model on this device's CPU. */
    public double estimatedTokensPerSec(String modelTier) {
        double base;
        if (ModelCatalog.TIER_06B.equals(modelTier)) base = 14.0;
        else if (ModelCatalog.TIER_17B.equals(modelTier)) base = 7.0;
        else base = 3.5;
        double coreFactor = Math.max(0.5, Math.min(1.6, cores / 4.0));
        double ramFactor = totalRam >= 6L * 1073741824L ? 1.15
                : totalRam >= 4L * 1073741824L ? 1.0 : 0.85;
        return base * coreFactor * ramFactor;
    }

    private static String recommendText(DeviceProfile d) {
        StringBuilder sb = new StringBuilder();
        String t = d.tier;
        if (TIER_TINY.equals(t)) {
            sb.append("Is phone par sirf halka model (0.6B) theek chalega. Cloud AI behtar jawab dega.");
        } else if (TIER_SMALL.equals(t)) {
            sb.append("1.7B Q2_K ya Q4_K_M theek chalega. Bada model slow hoga.");
        } else if (TIER_MEDIUM.equals(t)) {
            sb.append("1.7B Q4_K_M aaram se chalega — yahi best balance hai.");
        } else {
            sb.append("4B Hindi model bhi chal sakta hai (6GB+ RAM). Sabse achhi quality.");
        }
        if (!d.runtimeSupported) {
            sb.append(" Note: is build me on-device runtime available nahi hai — cloud/offline rules use honge.");
        }
        return sb.toString();
    }

    /** True when free RAM is tight — loading will be slow and may fail. */
    public boolean freeRamIsTight(long modelBytes) {
        return modelBytes > 0 && availRam > 0 && availRam < modelBytes;
    }

    public String ramText() {
        return String.format(Locale.US, "%.1f GB (free %.1f GB)",
                totalRam / 1073741824.0, availRam / 1073741824.0);
    }

    public String storageText() {
        return String.format(Locale.US, "%.1f GB free", freeStorage / 1073741824.0);
    }

    public String report() {
        StringBuilder sb = new StringBuilder();
        sb.append("Device tier: ").append(tier).append('\n');
        sb.append("RAM: ").append(ramText()).append('\n');
        sb.append("CPU cores: ").append(cores).append('\n');
        sb.append("ABI: ").append(abi).append("   Android ").append(sdk).append('\n');
        sb.append("Storage: ").append(storageText()).append('\n');
        sb.append("On-device runtime: ").append(runtimeSupported ? "available" : "not available").append('\n');
        sb.append('\n').append(recommendation);
        return sb.toString();
    }
}
