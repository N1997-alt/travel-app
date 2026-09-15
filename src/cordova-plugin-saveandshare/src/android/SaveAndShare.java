package cn.workbuddy.saveandshare;

import android.content.Intent;
import android.net.Uri;
import android.util.Base64;
import androidx.core.content.FileProvider;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.json.JSONArray;
import org.json.JSONException;
import java.io.File;
import java.io.FileOutputStream;

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
                        File cacheDir = cordova.getContext().getExternalCacheDir();
                        if (cacheDir == null) cacheDir = cordova.getContext().getCacheDir();
                        File out = new File(cacheDir, fileName);
                        byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                        FileOutputStream fos = new FileOutputStream(out);
                        fos.write(bytes);
                        fos.close();
                        String authority = cordova.getContext().getPackageName() + ".saveandshare.provider";
                        Uri uri = FileProvider.getUriForFile(cordova.getContext(), authority, out);
                        Intent share = new Intent(Intent.ACTION_SEND);
                        share.setType(mimeType);
                        share.putExtra(Intent.EXTRA_STREAM, uri);
                        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        Intent chooser = Intent.createChooser(share, "分享到");
                        cordova.getActivity().startActivity(chooser);
                        cb.success(out.getAbsolutePath());
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
