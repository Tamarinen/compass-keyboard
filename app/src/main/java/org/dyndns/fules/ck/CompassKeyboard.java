package org.dyndns.fules.ck;
import org.dyndns.fules.ck.R;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.XmlResourceParser;
import android.graphics.BitmapFactory;
import android.inputmethodservice.InputMethodService;
import org.dyndns.fules.inputmethodservice.KeyboardView.OnKeyboardActionListener;
import org.dyndns.fules.inputmethodservice.KeyboardView;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.R.id;
import androidx.core.app.NotificationCompat;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.Iterator;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import android.inputmethodservice.AbstractInputMethodService;
import android.view.ViewGroup;
import android.view.ViewParent;

public class CompassKeyboard extends InputMethodService implements KeyboardView.OnKeyboardActionListener, SharedPreferences.OnSharedPreferenceChangeListener  {
	public static final String	SHARED_PREFS_NAME = "CompassKeyboardSettings";
	public static final int[]	builtinLayouts = { R.xml.default_latin, R.xml.default_cyrillic, R.xml.default_greek }; // keep in sync with constants.xml
	private static final String	TAG = "CompassKeyboard";

	private SharedPreferences	mPrefs;					// the preferences instance
	CompassKeyboardView		ckv;					// the current layout view, either @ckv or @ckvVertical
	String				currentLayout;

	boolean				lastInPortrait;
	DisplayMetrics			lastMetrics = new DisplayMetrics();

	boolean				forcePortrait;				// use the portrait layout even for horizontal screens

	ExtractedTextRequest		etreq = new ExtractedTextRequest();
	int				selectionStart = -1, selectionEnd = -1;

	// Notification Channel stuff, required starting with Android 26
	public static final String NOTIFICATION_CHANNEL_ID = "ck_errors";
	public static final String CHANNEL_NAME = "Error Notifications";

	@Override
	public void onCreate() {
		super.onCreate();

		/*
		 * The notification stuff doesn't work yet. Besides, I'm not entirely
		 * convinced a notification is the best way to convey a temporary error message.
		 */

		// Notification channel should only be created for devices running Android 26 and up
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			int error_importance = NotificationManager.IMPORTANCE_DEFAULT;
			NotificationChannel notificationChannel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, CHANNEL_NAME, error_importance);
			notificationChannel.enableLights(false);
			notificationChannel.enableVibration(false);
			notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

			NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
			notificationManager.createNotificationChannel(notificationChannel);
		}
	}

	// send an auto-revoked notification with a title and a message
	void sendNotification(String title, String msg) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			//We pass the unique channel id as the second parameter in the constructor
			NotificationCompat.Builder notificationCompatBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
			//Title for your notification
			notificationCompatBuilder.setContentTitle(title);
			//Subtext for your notification
			notificationCompatBuilder.setContentText(msg);
			//Small Icon for your notification
			notificationCompatBuilder.setSmallIcon(R.drawable.icon_launcher);
			//Large Icon for your notification
			notificationCompatBuilder.setSmallIcon(R.drawable.icon_launcher);

			NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
			notificationManager.notify(42, notificationCompatBuilder.build());
		}
	}

	public void skipLayout(XmlPullParser parser) throws XmlPullParserException, IOException {
		while ((parser.getEventType() != XmlPullParser.END_TAG) || !parser.getName().contentEquals("Layout"))
			parser.nextTag();
		parser.nextTag();
	}
	// Read a layout from a parser
	String updateLayout(XmlPullParser parser) throws XmlPullParserException, java.io.IOException {
		String name;

		while (parser.getEventType() == XmlPullParser.START_DOCUMENT)
			parser.next();

		if ((parser.getEventType() != XmlPullParser.START_TAG) || !parser.getName().contentEquals("CompassKeyboard"))
			throw new XmlPullParserException("Expected <CompassKeyboard>", parser, null);

		name = parser.getAttributeValue(null, "name");
		if (name != null)
			Log.i(TAG, "Loading keyboard '"+name+"'");
		parser.nextTag();

		while (parser.getEventType() != XmlPullParser.END_TAG) {
			if ((parser.getEventType() != XmlPullParser.START_TAG) || !parser.getName().contentEquals("Layout"))
				throw new XmlPullParserException("Expected <Layout>", parser, null);
			String layoutName = parser.getAttributeValue(null, "name");

			if (layoutName.contentEquals("vertical")) {
				if (lastInPortrait) 
					ckv.readLayout(parser);
				else
					skipLayout(parser);
			}
			else if (layoutName.contentEquals("horizontal")) {
				if (!lastInPortrait) 
					ckv.readLayout(parser);
				else
					skipLayout(parser);
			}
			else 
				throw new XmlPullParserException("Invalid Layout name '"+layoutName+"'", parser, null);
		}
		ckv.calculateSizesForMetrics(lastMetrics);

		if (!parser.getName().contentEquals("CompassKeyboard"))
			throw new XmlPullParserException("Expected </CompassKeyboard>", parser, null);
		parser.next();

		return name;
	}

	public String updateLayout(String filename) {
		String result = "same";
		String err = null;

		if (filename.contentEquals(currentLayout))
			return result;
		try {
			if (filename.contentEquals("@latin"))
				result = updateLayout(getResources().getXml(R.xml.default_latin));
			else if (filename.contentEquals("@cyrillic"))
				result = updateLayout(getResources().getXml(R.xml.default_cyrillic));
			else if (filename.contentEquals("@greek"))
				result = updateLayout(getResources().getXml(R.xml.default_greek));
			else {
				FileInputStream is = new FileInputStream(filename);
				XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
				factory.setNamespaceAware(false);
				XmlPullParser parser = factory.newPullParser();
				parser.setInput(is, null);
				result = updateLayout(parser);
			}
		}
		catch (FileNotFoundException e)		{ err = e.getMessage(); }
		catch (XmlPullParserException e)	{ err = e.getMessage(); }
		catch (java.io.IOException e)		{ err = e.getMessage(); }

		if (err == null) {
			currentLayout = filename;	// loaded successfully, we may store it as 'current'
			return result;
		}

		sendNotification("Invalid layout", err);
		// revert to default latin, unless this was the one that has failed
		if (!filename.contentEquals("@latin")) {
			currentLayout = "";
			return updateLayout("@latin");
		}
		return "failed";
	}

	public String updateLayout(int i) {
		String s = mPrefs.getString("layout_path_" + i, "");
		if (s.length() > 0)
			return updateLayout(s);
		return "failed";
	}

	@Override public AbstractInputMethodService.AbstractInputMethodImpl onCreateInputMethodInterface() {
		Log.d(TAG, "onCreateInputMethodInterface;");
		etreq.hintMaxChars = etreq.hintMaxLines = 0;
		mPrefs = getSharedPreferences(SHARED_PREFS_NAME, 0);

		ckv = new CompassKeyboardView(this);
		ckv.setOnKeyboardActionListener(this);

		forcePortrait = mPrefs.getBoolean("portrait_only", false);
		lastMetrics.setTo(getResources().getDisplayMetrics());
		lastInPortrait = forcePortrait || (lastMetrics.widthPixels <= lastMetrics.heightPixels);

		currentLayout = "";			// enforce reloading layout
		updateLayout(mPrefs.getString("layout", "@latin"));

		ckv.setVibrateOnKey(getPrefInt("feedback_key", 0));
		ckv.setVibrateOnModifier(getPrefInt("feedback_mod", 0));
		ckv.setVibrateOnCancel(getPrefInt("feedback_cancel", 0));
		ckv.setFeedbackNormal(getPrefInt("feedback_text", 0));
		ckv.setFeedbackPassword(getPrefInt("feedback_password", 0));

		ckv.setLeftMargin(getPrefFloat("margin_left", 0));
		ckv.setRightMargin(getPrefFloat("margin_right", 0));
		ckv.setBottomMargin(getPrefFloat("margin_bottom", 0));
		ckv.setMaxKeySize(getPrefFloat("max_keysize", 12));

		mPrefs.registerOnSharedPreferenceChangeListener(this);
		return super.onCreateInputMethodInterface();
	}

	// Select the layout view appropriate for the screen direction, if there is more than one
	@Override public View onCreateInputView() {
		DisplayMetrics metrics = new DisplayMetrics();
		metrics.setTo(getResources().getDisplayMetrics());
		Log.v(TAG, "onCreateInputView; w=" + metrics.widthPixels + ", h=" + metrics.heightPixels + ", forceP=" + forcePortrait);
		Log.v(TAG, "onCreateInputView; last w=" + lastMetrics.widthPixels + ", h=" + lastMetrics.heightPixels + ", forceP=" + forcePortrait);
		if ((metrics.widthPixels != lastMetrics.widthPixels) || (metrics.heightPixels != lastMetrics.heightPixels)) {
			lastMetrics.setTo(metrics);
			boolean inPortrait = forcePortrait || (lastMetrics.widthPixels <= lastMetrics.heightPixels);
			Log.v(TAG, "onCreateInputView; metrics changed, inPortrait=" + inPortrait + ", lastInPortrait=" + lastInPortrait);
			if (inPortrait != lastInPortrait) {
				lastInPortrait = inPortrait;
				String s = currentLayout;
				currentLayout = "";			// enforce reloading layout
				updateLayout(s);
			}
			else {
				ckv.calculateSizesForMetrics(lastMetrics);	// don't reload, only resize
			}
		}

		ViewParent p = ckv.getParent();
		if ((p != null) && (p instanceof ViewGroup))
			((ViewGroup)p).removeView(ckv);
		return ckv;
	} 

	@Override public void onStartInputView(EditorInfo attribute, boolean restarting) {
		//Log.d(TAG, "onStartInputView;");
		super.onStartInputView(attribute, restarting);
		if (ckv != null) {
			ckv.resetState();
			ckv.setInputType(attribute.inputType);
		}
	}

	@Override public void onStartInput(EditorInfo attribute, boolean restarting) {
		super.onStartInput(attribute, restarting); 
		if (ckv != null) {
			ckv.resetState();
			ckv.setInputType(attribute.inputType);
		}
	}

	@Override public boolean onEvaluateFullscreenMode() {
		return false; // never require fullscreen
	}

	private void sendModifiers(InputConnection ic, int action) {
		if (ckv == null)
			return;

		if (ckv.checkState("shift"))
			ic.sendKeyEvent(new KeyEvent(action, KeyEvent.KEYCODE_SHIFT_LEFT));
		if (ckv.checkState("alt"))
			ic.sendKeyEvent(new KeyEvent(action, KeyEvent.KEYCODE_ALT_LEFT));
		if (ckv.checkState("altgr"))
			ic.sendKeyEvent(new KeyEvent(action, KeyEvent.KEYCODE_ALT_RIGHT));
	}

	// Process a generated keycode
	public void onKey(int primaryCode, int[] keyCodes) {
		InputConnection ic = getCurrentInputConnection();
		sendModifiers(ic, KeyEvent.ACTION_DOWN);
		sendDownUpKeyEvents(primaryCode);
		sendModifiers(ic, KeyEvent.ACTION_UP);
	}

	// Process the generated text
	public void onText(CharSequence text) {
		InputConnection ic = getCurrentInputConnection();
		sendModifiers(ic, KeyEvent.ACTION_DOWN);
		sendKeyChar(text.charAt(0));
	} 

	// Process a command
	public void execCmd(String cmd) {
		InputConnection ic = getCurrentInputConnection();

		if (cmd.equals("selectStart")) {
			selectionStart = ic.getExtractedText(etreq, 0).selectionStart;
			if ((selectionStart >= 0) && (selectionEnd >= 0)) {
				ic.setSelection(selectionStart, selectionEnd);
				selectionStart = selectionEnd = -1;
			}
		}
		else if (cmd.equals("selectEnd")) {
			selectionEnd = ic.getExtractedText(etreq, 0).selectionEnd;
			if ((selectionStart >= 0) && (selectionEnd >= 0)) {
				ic.setSelection(selectionStart, selectionEnd);
				selectionStart = selectionEnd = -1;
			}
		}
		else if (cmd.equals("selectAll"))
			ic.performContextMenuAction(android.R.id.selectAll);
		else if (cmd.equals("copy"))
			ic.performContextMenuAction(android.R.id.copy);
		else if (cmd.equals("cut"))
			ic.performContextMenuAction(android.R.id.cut);
		else if (cmd.equals("paste"))
			ic.performContextMenuAction(android.R.id.paste);
		else if (cmd.equals("switchIM"))
			ic.performContextMenuAction(android.R.id.switchInputMethod);
		else
			Log.w(TAG, "Unknown cmd '" + cmd + "'");
	}

	public void pickDefaultCandidate() {
	}

	public void swipeRight() {
	}

	public void swipeLeft() {
	}

	// Hide the view
	public void swipeDown() {
		requestHideSelf(0);
	}

	public void swipeUp() {
	}

	public void onPress(int primaryCode) {
	}

	public void onRelease(int primaryCode) {
	} 

	String getPrefString(String key, String def) {
		String s = "";

		if (lastInPortrait) {
			s = mPrefs.getString("portrait_" + key, "");
			if (s.contentEquals(""))
				s = mPrefs.getString("landscape_" + key, "");
		}
		else {
			s = mPrefs.getString("landscape_" + key, "");
			if (s.contentEquals(""))
				s = mPrefs.getString("portrait_" + key, "");
		}
		if (s.contentEquals(""))
			s = mPrefs.getString(key, "");
		return s.contentEquals("") ? def : s;
	}

	int getPrefInt(String key, int def) {
		String s = getPrefString(key, "");
		try {
			return s.contentEquals("") ? def : Integer.parseInt(s);
		}
		catch (NumberFormatException e) {
			Log.w(TAG, "Invalid value for integer preference; key='" + key + "', value='" + s +"'");
		}
		catch (ClassCastException e) {
			Log.w(TAG, "Found non-string int preference; key='" + key + "', err='" + e.getMessage() + "'");
		}
		return def;
	}

	float getPrefFloat(String key, float def) {
		String s = getPrefString(key, "");
		try {
			return s.contentEquals("") ? def : Float.parseFloat(s);
		}
		catch (NumberFormatException e) {
			Log.w(TAG, "Invalid value for float preference; key='" + key + "', value='" + s +"'");
		}
		catch (ClassCastException e) {
			Log.w(TAG, "Found non-string float preference; key='" + key + "', err='" + e.getMessage() + "'");
		}
		return def;
	}

	// Handle one change in the preferences
	public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
		//Log.d(TAG, "Changing pref "+key);
		if (key.contentEquals("feedback_key"))
			ckv.setVibrateOnKey(getPrefInt("feedback_key", 0));
		else if (key.contentEquals("feedback_mod"))
			ckv.setVibrateOnModifier(getPrefInt("feedback_mod", 0));
		else if (key.contentEquals("feedback_cancel"))
			ckv.setVibrateOnCancel(getPrefInt("feedback_cancel", 0));
		else if (key.contentEquals("feedback_text"))
			ckv.setFeedbackNormal(getPrefInt("feedback_text", 0));
		else if (key.contentEquals("feedback_password"))
			ckv.setFeedbackPassword(getPrefInt("feedback_password", 0));
		else if (key.endsWith("margin_left")) {
			ckv.setLeftMargin(getPrefFloat("margin_left", 0));
			getWindow().dismiss();
		}
		else if (key.endsWith("margin_right")) {
			ckv.setRightMargin(getPrefFloat("margin_right", 0));
			getWindow().dismiss();
		}
		else if (key.endsWith("margin_bottom")) {
			ckv.setBottomMargin(getPrefFloat("margin_bottom", 0));
			getWindow().dismiss();
		}
		else if (key.endsWith("max_keysize")) {
			ckv.setMaxKeySize(getPrefFloat("max_keysize", 12));
			getWindow().dismiss();
		}
		else if (key.contentEquals("layout"))
			updateLayout(mPrefs.getString("layout", "@latin"));
		else if (key.startsWith("layout_path_")) {
			int i = Integer.parseInt(key.substring(12));
			// ...
		}
		else if (key.contentEquals("portrait_only"))
			forcePortrait = mPrefs.getBoolean("portrait_only", false);
	}
}

// vim: set ai si sw=8 ts=8 noet:
