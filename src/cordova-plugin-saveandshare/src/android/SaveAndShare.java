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
import androidx.core.content.FileProvider;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.json.JSONArray;
import org.json.JSONException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class SaveAndShare extends CordovaPlugin {
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

                    // 1) 尽量写入系统「下载」公共目录（Android 10+ 走 MediaStore 免权限；旧系统直接写公共 Download）
                    String dlPath = writeToDownloads(bytes, fileName, mimeType);

                    // 2) 写一份到外部缓存（无权限问题），用 FileProvider 调起系统分享面板（微信/文件管理器/蓝牙）
                    File cacheDir = cordova.getContext().getExternalCacheDir();
                    if (cacheDir == null) cacheDir = cordova.getContext().getCacheDir();
                    File out = new File(cacheDir, fileName);
                    FileOutputStream fos = new FileOutputStream(out);
                    fos.write(bytes);
                    fos.close();

                    String authority = cordova.getContext().getPackageName() + ".saveandshare.provider";
                    Uri shareUri = FileProvider.getUriForFile(cordova.getContext(), authority, out);
                    Intent share = new Intent(Intent.ACTION_SEND);
                    share.setType(mimeType);
                    share.putExtra(Intent.EXTRA_STREAM, shareUri);
                    share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    Intent chooser = Intent.createChooser(share, "保存到 / 分享到");

                    try {
                        cordova.getActivity().startActivity(chooser);
                        if (dlPath != null) {
                            cb.success("已存入系统「下载」目录：" + dlPath + "（同时已弹出分享面板，可转发微信或文件管理器）");
                        } else {
                            cb.success("已生成文件，请用弹出的分享面板保存到手机或转发微信");
                        }
                    } catch (ActivityNotFoundException e) {
                        // 本机没有任何 App 能处理该分享
                        if (dlPath != null) {
                            cb.success("已存入系统「下载」目录：" + dlPath + "（本机无可用分享App，请到文件管理器 Download 目录查看）");
                        } else {
                            cb.error("保存失败：本机没有可处理该文件的App，且下载目录写入也失败");
                        }
                    }
                } catch (Exception e) {
                    cb.error("保存失败：" + (e.getMessage() == null ? e.toString() : e.getMessage()));
                }
            }
        });
        return true;
    }

    // 返回相对路径（如 Download/xxx.xlsx）表示成功；失败返回 null
    private String writeToDownloads(byte[] bytes, String fileName, String mimeType) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // Android 9 及以下：直接写公共 Download 目录（存储权限已在 config 声明）
            try {
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (dir != null) {
                    if (!dir.exists()) dir.mkdirs();
                    File f = new File(dir, fileName);
                    FileOutputStream fos = new FileOutputStream(f);
                    fos.write(bytes);
                    fos.close();
                    return "Download/" + fileName;
                }
            } catch (Exception ignore) { }
            return null;
        }
        // Android 10+：MediaStore 免权限写入公共下载目录
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
            return "Download/" + fileName;
        } catch (Exception ignore) { }
        return null;
    }
}
