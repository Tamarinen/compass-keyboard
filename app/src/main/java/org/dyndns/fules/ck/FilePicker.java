package org.dyndns.fules.ck;
import org.dyndns.fules.ck.R;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;
import android.view.View;
import android.app.Activity;
import android.app.ActivityGroup;
import android.content.Intent;
import android.os.Bundle;
import android.net.Uri;
import java.util.Iterator;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class FilePicker extends Activity implements FilePickerView.ResultListener {
	private static final String TAG = "FilePicker";
	//private static final Integer MY_PERMISSIONS_REQUEST_READ_STORAGE = 10010;

	public static final String ACTION_PICK = "org.dyndns.fules.ck.filepicker.action.PICK";
    public static final String EXTRA_PATH = "org.dyndns.fules.ck.filepicker.extra.path";
	public static final String EXTRA_REGEX = "org.dyndns.fules.ck.filepicker.extra.regex";
	public static final String EXTRA_SHOW_HIDDEN = "org.dyndns.fules.ck.filepicker.extra.show.hidden";
	public static final String EXTRA_SHOW_FILES = "org.dyndns.fules.ck.filepicker.extra.show.files";
	public static final String EXTRA_SHOW_OTHERS = "org.dyndns.fules.ck.filepicker.extra.show.others";
	public static final String EXTRA_SHOW_UNREADABLE = "org.dyndns.fules.ck.filepicker.extra.show.unreadable";
	public static final String EXTRA_PREFERENCE = "org.dyndns.fules.ck.filepicker.extra.preference";
	public static final String EXTRA_PREFERENCE_KEY = "org.dyndns.fules.ck.filepicker.extra.preference.key";

    String prefName = null;
    String prefKey = null;

	@Override public void
	onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		/*
		 * This is not the way to do it in a modern app,
		 * but we're at least not trying to read things
		 * we're not allowed to read.
		 */
		Context thisActivity = getApplicationContext();
		if (ContextCompat.checkSelfPermission(thisActivity,
				Manifest.permission.READ_EXTERNAL_STORAGE)
				!= PackageManager.PERMISSION_GRANTED) {
			/*
			 * Is it even possible to ask for permissions inside onCreate()?
			 */
			//ActivityCompat.requestPermissions(thisActivity,
			// 	    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
			//    	MY_PERMISSIONS_REQUEST_READ_STORAGE);
			setContentView(R.layout.fileaccesserror);
			return;
		}
		setContentView(R.layout.filepicker);

		Intent i = getIntent();
        {
            Bundle bundle = i.getExtras();
            if (bundle == null) {
                Log.d(TAG, "No extras");
            }
            else {
                for (String key : bundle.keySet()) {
                    Object value = bundle.get(key);
                    Log.d(TAG, String.format("Extra: %s %s (%s)", key,  value.toString(), value.getClass().getName()));
                }
            }
        }
		String action = i.getAction();
		if (action.contentEquals(Intent.ACTION_MAIN) || action.contentEquals(ACTION_PICK)) {
			String s;
			int n;

			FilePickerView fp = (FilePickerView)findViewById(R.id.filepicker);
			fp.setResultListener(this);

			s = i.getStringExtra(EXTRA_PATH);
			if (s != null)
				fp.setWorkingDir(s);

			s = i.getStringExtra(EXTRA_REGEX);
			if (s != null)
				fp.setRegex(s);

			n = i.getIntExtra(EXTRA_SHOW_HIDDEN, -1);
			if (n != -1)
				fp.setShowHidden(n != 0);

			n = i.getIntExtra(EXTRA_SHOW_FILES, -1);
			if (n != -1)
				fp.setShowFiles(n != 0);

			n = i.getIntExtra(EXTRA_SHOW_OTHERS, -1);
			if (n != -1)
				fp.setShowOthers(n != 0);

			n = i.getIntExtra(EXTRA_SHOW_UNREADABLE, -1);
			if (n != -1)
				fp.setShowUnreadable(n != 0);

			prefName = i.getStringExtra(EXTRA_PREFERENCE);
			prefKey = i.getStringExtra(EXTRA_PREFERENCE_KEY);
		}
		else {
			Log.e(TAG, "Unsupported action; value='" + action + "'");
			setResult(Activity.RESULT_CANCELED);
			finish();
		}
	}

	public void onFileSelected(String path, boolean selected) {
		Log.d(TAG, "Selected file; path='" + path + "', state='" + selected + "'");
        if ((prefName != null) && (prefKey != null) && (prefName.length() > 0) && (prefKey.length() > 0)) {
            Log.d(TAG, "Shared pref; name='" + prefName + "', key='" + prefKey + "'");
            SharedPreferences prefs = getSharedPreferences(prefName, 0);
            SharedPreferences.Editor ed = prefs.edit();
            ed.putString(prefKey, path);
            ed.apply();
        }
		setResult(Activity.RESULT_OK, new Intent().setAction(ACTION_PICK).putExtra(EXTRA_PATH, path));
		finish();
	}
}
