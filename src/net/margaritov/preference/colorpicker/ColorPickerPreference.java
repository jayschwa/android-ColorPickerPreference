/*
 * Copyright (C) 2011 Sergey Margaritov
 * Copyright (C) 2012 Jay Weisskopf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.margaritov.preference.colorpicker;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Bitmap.Config;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

/**
 * A preference type for choosing a color.
 * 
 * @author Sergey Margaritov
 * @author Jay Weisskopf
 */
public class ColorPickerPreference extends DialogPreference {

	View mView;
	private int mColor = Color.BLACK;
	private float mDensity = 0;
	private boolean mAlphaSliderEnabled = false;
	private ColorPickerView mColorPicker;
	private ColorPickerPanelView mOldColor;
	private ColorPickerPanelView mNewColor;

	public ColorPickerPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(context, attrs);
	}

	public ColorPickerPreference(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init(context, attrs);
	}

	@Override
	protected Object onGetDefaultValue(TypedArray a, int index) {
		return a.getColor(index, Color.BLACK);
	}

	@Override
	protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
		setColor(restoreValue ? getPersistedInt(mColor) : (Integer) defaultValue, false);
	}

	private void init(Context context, AttributeSet attrs) {
		mDensity = getContext().getResources().getDisplayMetrics().density;
		if (attrs != null) {
			mAlphaSliderEnabled = attrs.getAttributeBooleanValue(null, "alphaSlider", false);
		}

		setDialogLayoutResource(R.layout.dialog_color_picker);
	}

	protected View onCreateDialogView() {
		View dialogView = super.onCreateDialogView();

		mColorPicker = (ColorPickerView) dialogView.findViewById(R.id.color_picker_view);
		mOldColor = (ColorPickerPanelView) dialogView.findViewById(R.id.old_color_panel);
		mNewColor = (ColorPickerPanelView) dialogView.findViewById(R.id.new_color_panel);

		((LinearLayout) mOldColor.getParent()).setPadding(
				Math.round(mColorPicker.getDrawingOffset()), 0,
				Math.round(mColorPicker.getDrawingOffset()), 0);

		mColorPicker.setAlphaSliderVisible(mAlphaSliderEnabled);
		mColorPicker.setOnColorChangedListener(mNewColor);
		mColorPicker.setColor(mColor);
		mOldColor.setColor(mColor);

		// Simulate "Cancel" when old color panel is clicked.
		mOldColor.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				Dialog dialog = ColorPickerPreference.this.getDialog();
				ColorPickerPreference.this.onClick(dialog, DialogInterface.BUTTON_NEGATIVE);
				dialog.cancel();
			}
		});

		// Simulate "OK" when new color panel is clicked.
		mNewColor.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				Dialog dialog = ColorPickerPreference.this.getDialog();
				ColorPickerPreference.this.onClick(dialog, DialogInterface.BUTTON_POSITIVE);
				dialog.dismiss();
			}
		});

		return dialogView;
	}

	protected void showDialog(Bundle state) {
		super.showDialog(state);
		// Specify higher quality pixel format to avoid color banding
		getDialog().getWindow().setFormat(PixelFormat.RGBA_8888);
	}

	public void onDismiss(DialogInterface dialog) {
		// Reset pixel format
		getDialog().getWindow().setFormat(PixelFormat.UNKNOWN);
		super.onDismiss(dialog);
	}

	protected void onDialogClosed(boolean positiveResult) {
		int newColor = mNewColor.getColor();
		if (positiveResult && callChangeListener(newColor)) {
			setColor(newColor, true);
		}
		super.onDialogClosed(positiveResult);
	}

	@Override
	protected void onBindView(View view) {
		super.onBindView(view);
		mView = view;
		setPreviewColor();
	}

	private void setPreviewColor() {
		if (mView == null)
			return;
		LinearLayout widgetFrameView = ((LinearLayout) mView
				.findViewById(android.R.id.widget_frame));
		if (widgetFrameView == null)
			return;
		widgetFrameView.setVisibility(View.VISIBLE);
		widgetFrameView.setPadding(widgetFrameView.getPaddingLeft(),
				widgetFrameView.getPaddingTop(), (int) (mDensity * 8),
				widgetFrameView.getPaddingBottom());
		widgetFrameView.setMinimumWidth(0);

		// Get existing ImageView or create a new one
		ImageView iView;
		if (widgetFrameView.getChildCount() > 0) {
			iView = (ImageView) widgetFrameView.getChildAt(0);
		} else {
			iView = new ImageView(getContext());
			widgetFrameView.addView(iView);
			iView.setBackgroundDrawable(new AlphaPatternDrawable((int) (5 * mDensity)));
		}
		iView.setImageBitmap(getPreviewBitmap());
	}

	private Bitmap getPreviewBitmap() {
		int d = (int) (mDensity * 31); // 30dip
		int color = mColor;
		// FIXME: Is this Bitmap ever getting recycled? Potential memory leak.
		Bitmap bm = Bitmap.createBitmap(d, d, Config.ARGB_8888);
		int w = bm.getWidth();
		int h = bm.getHeight();
		int c = color;
		for (int i = 0; i < w; i++) {
			for (int j = i; j < h; j++) {
				c = (i <= 1 || j <= 1 || i >= w - 2 || j >= h - 2) ? Color.GRAY : color;
				bm.setPixel(i, j, c);
				if (i != j) {
					bm.setPixel(j, i, c);
				}
			}
		}

		return bm;
	}

	public void setColor(int color, boolean notify) {
		if (color == mColor) {
			return;
		}
		if (isPersistent()) {
			persistInt(color);
		}
		mColor = color;
		setPreviewColor();
		if (notify) {
			notifyChanged();
		}
	}

	/**
	 * Toggle Alpha Slider visibility (by default it's disabled)
	 * 
	 * @param enable
	 */
	public void setAlphaSliderEnabled(boolean enable) {
		mAlphaSliderEnabled = enable;
	}

	/**
	 * For custom purposes. Not used by ColorPickerPreference
	 * 
	 * @param color
	 * @author Unknown
	 */
	public static String convertToARGB(int color) {
		String alpha = Integer.toHexString(Color.alpha(color));
		String red = Integer.toHexString(Color.red(color));
		String green = Integer.toHexString(Color.green(color));
		String blue = Integer.toHexString(Color.blue(color));

		if (alpha.length() == 1) {
			alpha = "0" + alpha;
		}

		if (red.length() == 1) {
			red = "0" + red;
		}

		if (green.length() == 1) {
			green = "0" + green;
		}

		if (blue.length() == 1) {
			blue = "0" + blue;
		}

		return "#" + alpha + red + green + blue;
	}

	/**
	 * For custom purposes. Not used by ColorPickerPreference
	 * 
	 * @param argb
	 * @throws NumberFormatException
	 * @author Unknown
	 */
	public static int convertToColorInt(String argb) throws NumberFormatException {

		if (argb.startsWith("#")) {
			argb = argb.replace("#", "");
		}

		int alpha = -1, red = -1, green = -1, blue = -1;

		if (argb.length() == 8) {
			alpha = Integer.parseInt(argb.substring(0, 2), 16);
			red = Integer.parseInt(argb.substring(2, 4), 16);
			green = Integer.parseInt(argb.substring(4, 6), 16);
			blue = Integer.parseInt(argb.substring(6, 8), 16);
		} else if (argb.length() == 6) {
			alpha = 255;
			red = Integer.parseInt(argb.substring(0, 2), 16);
			green = Integer.parseInt(argb.substring(2, 4), 16);
			blue = Integer.parseInt(argb.substring(4, 6), 16);
		}

		return Color.argb(alpha, red, green, blue);
	}

	@Override
	protected Parcelable onSaveInstanceState() {
		final Parcelable superState = super.onSaveInstanceState();

		final SavedState myState = new SavedState(superState);
		myState.mOldColor = mColor;
		myState.mNewColor = mNewColor != null ? mNewColor.getColor() : mColor;
		myState.mAlphaSliderEnabled = mAlphaSliderEnabled;

		return myState;
	}

	@Override
	protected void onRestoreInstanceState(Parcelable state) {
		if (state == null || !(state instanceof SavedState)) {
			// Didn't save state for us in onSaveInstanceState
			super.onRestoreInstanceState(state);
			return;
		}

		SavedState myState = (SavedState) state;
		mColor = myState.mOldColor;
		mAlphaSliderEnabled = myState.mAlphaSliderEnabled;

		// Dialog (if one was showing) will be re-created here
		super.onRestoreInstanceState(myState.getSuperState());

		if (mColorPicker != null) {
			mColorPicker.setColor(myState.mNewColor);
		}
	}

	private static class SavedState extends BaseSavedState {
		int mOldColor;
		int mNewColor;
		boolean mAlphaSliderEnabled;

		public SavedState(Parcel source) {
			super(source);
			mOldColor = source.readInt();
			mNewColor = source.readInt();
			mAlphaSliderEnabled = source.readByte() == 1;
		}

		@Override
		public void writeToParcel(Parcel dest, int flags) {
			super.writeToParcel(dest, flags);
			dest.writeInt(mOldColor);
			dest.writeInt(mNewColor);
			dest.writeByte((byte) (mAlphaSliderEnabled ? 1 : 0));
		}

		public SavedState(Parcelable superState) {
			super(superState);
		}

		@SuppressWarnings("unused")
		public static final Parcelable.Creator<SavedState> CREATOR = new Parcelable.Creator<SavedState>() {
			public SavedState createFromParcel(Parcel in) {
				return new SavedState(in);
			}

			public SavedState[] newArray(int size) {
				return new SavedState[size];
			}
		};
	}
}