package cn.workbuddy.saveandshare;

import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import androidx.core.content.FileProvider;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.json.JSONArray;
import org.json.JSONException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class SaveAndShare extends CordovaPlugin {
    private static final String TAG = "SaveAndShare";

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if (!"saveAndShare".equals(action)) return false;
        final String base64 = args.getString(0);
        final String rawName = args.getString(1);
        final String rawMime = args.getString(2);
        final CallbackContext cb = callbackContext;
        cordova.getActivity().runOnUiThread(new Runnable() {
            public void run() {
                try {
                    byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                    String fileName = (rawName == null || rawName.trim().isEmpty()) ? "file.dat" : rawName.trim();
                    String mimeType = (rawMime == null || rawMime.trim().isEmpty()) ? "application/octet-stream" : rawMime.trim();

                    // 1) 应用私有外部缓存目录（任何 Android 版本都无需权限、必定可写；用于调起系统分享面板）
                    File cacheDir = cordova.getContext().getExternalCacheDir();
                    if (cacheDir == null) cacheDir = cordova.getContext().getCacheDir();
                    File shareFile = new File(cacheDir, fileName);
                    writeBytes(shareFile, bytes);

                    // 2) 应用私有文件目录的 Download 子目录（无需权限、必定可写、用户可在文件管理器找到）
                    File extFiles = new File(cordova.getContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName);
                    writeBytes(extFiles, bytes);

                    // 3) 尽量再写一份到系统「下载」公共目录（Android 10+ 走 MediaStore 免权限）
                    String pubPath = writeToPublicDownloads(bytes, fileName, mimeType);

                    // 4) 用必定存在的缓存文件调起系统分享面板（微信 / 文件管理器 / 蓝牙）
                    String authority = cordova.getContext().getPackageName() + ".saveandshare.provider";
                    Uri shareUri = FileProvider.getUriForFile(cordova.getContext(), authority, shareFile);
                    Intent share = new Intent(Intent.ACTION_SEND);
                    share.setType(mimeType);
                    share.putExtra(Intent.EXTRA_STREAM, shareUri);
                    share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    Intent chooser = Intent.createChooser(share, "保存到 / 分享到");

                    String where = (pubPath != null)
                            ? ("系统「下载」目录：" + pubPath)
                            : ("文件管理器路径：" + extFiles.getAbsolutePath());

                    try {
                        cordova.getActivity().startActivity(chooser);
                        cb.success("已保存 ✓ " + where + "\n（已弹出分享面板，可转发微信；也可在文件管理器打开该路径）");
                    } catch (ActivityNotFoundException e) {
                        // 本机没有任何 App 能处理分享：文件已落盘，直接告知路径
                        cb.success("已保存 ✓ " + where + "\n（本机无可用分享App，请用文件管理器打开该路径）");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "saveAndShare failed", e);
                    cb.error("保存失败：" + (e.getMessage() == null ? e.toString() : e.getMessage()));
                }
            }
        });
        return true;
    }

    private void writeBytes(File f, byte[] bytes) throws Exception {
        File p = f.getParentFile();
        if (p != null && !p.exists()) p.mkdirs();
        FileOutputStream fos = new FileOutputStream(f);
        fos.write(bytes);
        fos.close();
    }

    // 返回相对/绝对路径表示成功；失败返回 null
    private String writeToPublicDownloads(byte[] bytes, String fileName, String mimeType) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            try {
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (dir != null) {
                    if (!dir.exists()) dir.mkdirs();
                    File f = new File(dir, fileName);
                    writeBytes(f, bytes);
                    return f.getAbsolutePath();
                }
            } catch (Exception ignore) { }
            return null;
        }
        try {
            ContentResolver cr = cordova.getActivity().getContentResolver();
            ContentValues v = new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
            v.put(MediaStore.Downloads.MIME_TYPE, mimeType);
            v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                v.put(MediaStore.MediaColumns.IS_PENDING, 1);
            }
            Uri uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri == null) return null;
            OutputStream os = cr.openOutputStream(uri);
            os.write(bytes);
            os.close();
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                v.clear();
                v.put(MediaStore.MediaColumns.IS_PENDING, 0);
                cr.update(uri, v, null, null);
            }
            return Environment.DIRECTORY_DOWNLOADS + "/" + fileName;
        } catch (Exception ignore) { }
        return null;
    }
}
