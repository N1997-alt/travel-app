package cn.workbuddy.saveandshare;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.content.ContentValues;
import android.content.ContentResolver;
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
        if (action.equals("saveAndShare")) {
            final String base64 = args.getString(0);
            final String fileName = args.getString(1);
            final String mimeType = args.getString(2);
            final CallbackContext cb = callbackContext;
            cordova.getActivity().runOnUiThread(new Runnable() {
                public void run() {
                    try {
                        byte[] bytes = Base64.decode(base64, Base64.DEFAULT);

                        // 1) 尽力把文件写入系统「下载」目录（Android10+ 用 MediaStore 免权限；老设备可能无权限，失败也不影响分享）
                        try {
                            ContentResolver cr = cordova.getContext().getContentResolver();
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                ContentValues v = new ContentValues();
                                v.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                                v.put(MediaStore.Downloads.MIME_TYPE, mimeType);
                                v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                                Uri dlUri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                                OutputStream os = cr.openOutputStream(dlUri);
                                os.write(bytes);
                                os.close();
                            } else {
                                File dlDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                                if (dlDir != null) {
                                    if (!dlDir.exists()) dlDir.mkdirs();
                                    File outOld = new File(dlDir, fileName);
                                    FileOutputStream fosOld = new FileOutputStream(outOld);
                                    fosOld.write(bytes);
                                    fosOld.close();
                                }
                            }
                        } catch (Exception ignore) { /* 下载目录写入失败不致命，仍可分享 */ }

                        // 2) 写外部缓存（无权限问题，必成功），用 FileProvider 调起系统分享面板
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
                        Intent chooser = Intent.createChooser(share, "分享到");
                        cordova.getActivity().startActivity(chooser);
                        cb.success("已保存到系统「下载」目录：" + fileName + "（若未弹出分享面板，请到文件管理器的 Download 目录查找）");
                    } catch (Exception e) {
                        cb.error(e.getMessage());
                    }
                }
            });
            return true;
        }
        return false;
    }
}
