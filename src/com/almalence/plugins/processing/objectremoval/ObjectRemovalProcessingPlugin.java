/*
The contents of this file are subject to the Mozilla Public License
Version 1.1 (the "License"); you may not use this file except in
compliance with the License. You may obtain a copy of the License at
http://www.mozilla.org/MPL/

Software distributed under the License is distributed on an "AS IS"
basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
License for the specific language governing rights and limitations
under the License.

The Original Code is collection of files collectively known as Open Camera.

The Initial Developer of the Original Code is Almalence Inc.
Portions created by Initial Developer are Copyright (C) 2013 
by Almalence Inc. All Rights Reserved.
*/

package com.almalence.plugins.processing.objectremoval;

import java.util.ArrayList;
import java.util.Arrays;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.view.Display;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;

import com.almalence.SwapHeap;
/* <!-- +++
import com.almalence.opencam_plus.MainScreen;
import com.almalence.opencam_plus.PluginManager;
import com.almalence.opencam_plus.PluginProcessing;
import com.almalence.opencam_plus.R;
+++ --> */
// <!-- -+-
import com.almalence.opencam.MainScreen;
import com.almalence.opencam.PluginManager;
import com.almalence.opencam.R;
import com.almalence.opencam.cameracontroller.CameraController;
//-+- -->

import com.almalence.util.Size;
import com.almalence.plugins.processing.objectremoval.AlmaCLRShot.ObjBorderInfo;
import com.almalence.plugins.processing.objectremoval.AlmaCLRShot.ObjectInfo;
import com.almalence.plugins.processing.objectremoval.AlmaCLRShot.OnProcessingListener;

/***
Implements night processing
***/

public class ObjectRemovalProcessingPlugin implements Handler.Callback, OnClickListener {
	
	private View postProcessingView;
	
	private long sessionID=0;
	private static int mSensitivity = 15;
    private static int mMinSize = 1000;
    private static String mGhosting = "0";
    
    private static int mAngle = 0;

    private boolean released=false;
    private AlmaCLRShot mAlmaCLRShot;

    private static int imgWidthOR;
    private static int imgHeightOR;
	private int mDisplayOrientation;
	private boolean mCameraMirrored;
	
	//indicates that no more user interaction needed
	private boolean finishing = false;
		
	public View getPostProcessingView() {
		return postProcessingView;
	}
	
	public void onStart() {
		getPrefs();
	}
	
	public void onStartProcessing(long SessionID) {
		finishing = false;
		released=false;
		Message msg = new Message();
		msg.what = PluginManager.MSG_PROCESSING_BLOCK_UI;
		MainScreen.getMessageHandler().sendMessage(msg);	
		
		Message msg2 = new Message();
		msg2.arg1 = PluginManager.MSG_CONTROL_LOCKED;
		msg2.what = PluginManager.MSG_BROADCAST;
		MainScreen.getMessageHandler().sendMessage(msg2);
		
		MainScreen.getGUIManager().lockControls = true;
		
		sessionID=SessionID;

		PluginManager.getInstance().addToSharedMem("modeSaveName"+Long.toString(sessionID), PluginManager.getInstance().getActiveMode().modeSaveName);
		
		mDisplayOrientation = MainScreen.getGUIManager().getDisplayOrientation();
    	mCameraMirrored = CameraController.isFrontCamera();
    	
    	int iSaveImageWidth = MainScreen.getSaveImageWidth();
		int iSaveImageHeight = MainScreen.getSaveImageHeight();
        
        if(mDisplayOrientation == 0 || mDisplayOrientation == 180)
        {
        	imgWidthOR = MainScreen.getImageHeight();
        	imgHeightOR = MainScreen.getImageWidth();
        }
        else
        {
        	imgWidthOR = MainScreen.getImageWidth();
        	imgHeightOR = MainScreen.getImageHeight();
        }
        
        boolean isYUV = Boolean.parseBoolean(PluginManager.getInstance().getFromSharedMem("isyuv"+Long.toString(sessionID)));
        mYUVBufferList.clear();
		
		mAlmaCLRShot = AlmaCLRShot.getInstance();
		
        getPrefs();
         
     	try {
     		Size input = new Size(MainScreen.getImageWidth(), MainScreen.getImageHeight());
            int imagesAmount = Integer.parseInt(PluginManager.getInstance().getFromSharedMem("amountofcapturedframes"+Long.toString(sessionID)));
     		int minSize = 1000;
     		if (mMinSize == 0) {
     			minSize = 0;
     		} else {
     			minSize = input.getWidth() * input.getHeight() / mMinSize;
     		}
    		
    		if (imagesAmount==0)
    			imagesAmount=1;
    		
    		mJpegBufferList.clear();
    		for (int i=1; i<=imagesAmount; i++)
    		{
    			byte[] in = SwapHeap.CopyFromHeap(
    	        		Integer.parseInt(PluginManager.getInstance().getFromSharedMem("frame" + i+Long.toString(sessionID))),
    	        		Integer.parseInt(PluginManager.getInstance().getFromSharedMem("framelen" + i+Long.toString(sessionID)))
    	        		);
    			
    			mJpegBufferList.add(i-1, in);
    		}
    		
    		getDisplaySize(mJpegBufferList.get(0));
    		Size preview = new Size(mDisplayWidth, mDisplayHeight);
    		
    		PluginManager.getInstance().addToSharedMem("amountofresultframes"+Long.toString(sessionID), String.valueOf(imagesAmount));
    		
    		PluginManager.getInstance().addToSharedMem("saveImageWidth"+String.valueOf(sessionID), String.valueOf(iSaveImageWidth));
        	PluginManager.getInstance().addToSharedMem("saveImageHeight"+String.valueOf(sessionID), String.valueOf(iSaveImageHeight));   	
        	
     		//frames!!! should be taken from heap
     		mAlmaCLRShot.addInputFrame(mJpegBufferList, input, isYUV);

     		mAlmaCLRShot.initialize(preview,
     				mAngle,
 					/*
 					 * -1 : auto mode
 					 * 0 ~ max number of input frame : manual mode
 					 */
 					-1,
 					/*
 					 * sensitivity for objection detection
 					 * 
 					 */
 					mSensitivity - 15,
 					/*
 					 *  Minimum size of object to be able to detect
 					 *  -15 ~ 15
 					 *  max -> easy detection dull detection
 					 *  min -> 
 					 */
 					minSize,
 					/*
 					 * ghosting parameter
    					 * 0 : normal operation
 					 * 1 : detect ghosted objects but not remove them
 					 * 2 : detect and remove all object
 					 */
 					Integer.parseInt(mGhosting),
 					mOnProcessingListener);
 		} 
     	catch (Exception e) 
 		{
 			e.printStackTrace();
 		}
     }
		
/************************************************
 * 		POST PROCESSING
 ************************************************/
	public boolean isPostProcessingNeeded() {
		return true;
	}
	
	private ImageView mImgView;
	private Button mSaveButton;
	
	private int mLayoutOrientationCurrent;
	private int mDisplayOrientationCurrent;
	
	private static final int MSG_REDRAW = 1;
	private static final int MSG_LEAVING = 3;
	private static final int MSG_END_OF_LOADING = 4;
	private static final int MSG_SAVE = 5;
	private final Handler mHandler = new Handler(this);
	private boolean[] mObjStatus;
	private Bitmap PreviewBmp = null;
	public static int mDisplayWidth;
	public static int mDisplayHeight;
	
	public static ArrayList<byte[]> mJpegBufferList;
	public static void setmJpegBufferList(ArrayList<byte[]> mJpegBufferList) {
		ObjectRemovalProcessingPlugin.mJpegBufferList = mJpegBufferList;
	}

	public static ArrayList<Integer> mYUVBufferList = new ArrayList<Integer>();
//	public static void setmYUVBufferList(ArrayList<Integer> mYUVBufferList) {
//		ObjectRemovalProcessingPlugin.mYUVBufferList = mYUVBufferList;
//	}
	
	Paint paint=null;
	
	private boolean postProcessingRun = false;
	
	public void onStartPostProcessing() {	
		mDisplayOrientationCurrent = MainScreen.getGUIManager().getDisplayOrientation();
		int orientation = MainScreen.getGUIManager().getLayoutOrientation();    	
    	mLayoutOrientationCurrent = (orientation == 0 || orientation == 180)? orientation: (orientation + 180)%360;
		
		LayoutInflater inflator = MainScreen.getInstance().getLayoutInflater();
		postProcessingView = inflator.inflate(R.layout.plugin_processing_objectremoval_postprocessing, null, false);
		
		mImgView = ((ImageView)postProcessingView.findViewById(R.id.objectremovalImageHolder));
		
		mObjStatus = new boolean[mAlmaCLRShot.getTotalObjNum()];
        Arrays.fill(mObjStatus, true);

        if (PreviewBmp != null) {
        	PreviewBmp.recycle();
        }

		paint = new Paint();
		paint.setColor(0xFF00AAEA);
		paint.setStrokeWidth(5);
		paint.setPathEffect(new DashPathEffect(new float[] {5,5},0));

    	PreviewBmp = mAlmaCLRShot.getPreviewBitmap();
    	drawObjectRectOnBitmap(PreviewBmp, mAlmaCLRShot.getObjectInfoList(), mAlmaCLRShot.getObjBorderBitmap(paint));

        if (PreviewBmp != null)  
        {
        	Matrix matrix = new Matrix();
        	matrix.postRotate(90);
        	Bitmap rotated = Bitmap.createBitmap(PreviewBmp, 0, 0, PreviewBmp.getWidth(), PreviewBmp.getHeight(),
        	        matrix, true);
        	mImgView.setImageBitmap(rotated);
        	mImgView.setRotation(CameraController.isFrontCamera()? ((mDisplayOrientation == 0 || mDisplayOrientation == 180) ? 0 : 180) : 0);
        }		

	    mHandler.sendEmptyMessage(MSG_END_OF_LOADING);
	}
	
	public void getDisplaySize(byte[] data) 
	{
		Display display= ((WindowManager) MainScreen.getInstance().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
		Point dis = new Point();
		display.getSize(dis);

		float imageRatio = (float)MainScreen.getImageWidth() / (float)MainScreen.getImageHeight();
		float displayRatio = (float)dis.y / (float)dis.x;
		
		if (imageRatio > displayRatio) {
			mDisplayWidth = dis.y;
			mDisplayHeight = (int)((float)dis.y / (float)imageRatio);
		} else {
			mDisplayWidth = (int)((float)dis.x * (float)imageRatio);
			mDisplayHeight = dis.x;
		}
		return;
	}
	
	private void setupImageView() {
		mImgView.setOnTouchListener(new View.OnTouchListener()
        {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				if (event.getAction() == MotionEvent.ACTION_DOWN)
				{
					if (finishing)
						return true;
			        float x = event.getY();
					float y = mDisplayHeight-1-event.getX();
					int objIndex = 0;
					try {
						objIndex = mAlmaCLRShot.getOccupiedObject(x, y);
					} catch (Exception e) {
						e.printStackTrace();
					}

					if (objIndex >= 1) {
						mObjStatus[objIndex-1] = !mObjStatus[objIndex-1];
					}
					mHandler.sendEmptyMessage(MSG_REDRAW);
				}
				return false;
			}
        });
    }
	
    public void setupSaveButton() {
    	// put save button on screen
        mSaveButton = new Button(MainScreen.getInstance());
        mSaveButton .setBackgroundResource(R.drawable.button_save_background);
        mSaveButton .setOnClickListener(this);
        LayoutParams saveLayoutParams = new LayoutParams(
        		(int) (MainScreen.getMainContext().getResources().getDimension(R.dimen.postprocessing_savebutton_size)), 
        		(int) (MainScreen.getMainContext().getResources().getDimension(R.dimen.postprocessing_savebutton_size)));
        saveLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        saveLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
        saveLayoutParams.setMargins(
        		(int)(MainScreen.getInstance().getResources().getDisplayMetrics().density * 8), 
        		(int)(MainScreen.getInstance().getResources().getDisplayMetrics().density * 8), 
        		0, 
        		0);
		((RelativeLayout)postProcessingView.findViewById(R.id.objectremovalLayout)).addView(mSaveButton, saveLayoutParams);
		mSaveButton.setRotation(mLayoutOrientationCurrent);
    }
    
    public void onOrientationChanged(int orientation)
    {	    	
    	if(orientation != mDisplayOrientationCurrent)
    	{
    		mLayoutOrientationCurrent = (orientation == 0 || orientation == 180) ? orientation + 90 : orientation - 90;
    		mDisplayOrientationCurrent = orientation;
    		if(postProcessingRun)
    			mSaveButton.setRotation(mLayoutOrientationCurrent);
    	}
    }
    
    @Override
	public void onClick(View v) 
	{
    	if (v == mSaveButton)
    	{
    		if (finishing)
				return;
    		finishing = true;
    		mHandler.sendEmptyMessage(MSG_SAVE);
    	}
	}
    
    public void savePicture(Context context)
    {
    	byte[] result = mAlmaCLRShot.processingSaveData();
		int frame_len = result.length;
		int frame = SwapHeap.SwapToHeap(result);
		PluginManager.getInstance().addToSharedMem("resultframeformat1"+Long.toString(sessionID), "jpeg");
		PluginManager.getInstance().addToSharedMem("resultframe1"+Long.toString(sessionID), String.valueOf(frame));
    	PluginManager.getInstance().addToSharedMem("resultframelen1"+Long.toString(sessionID), String.valueOf(frame_len));
    	
    	PluginManager.getInstance().addToSharedMem("resultframeorientation1" + String.valueOf(sessionID), String.valueOf(mDisplayOrientation));
    	PluginManager.getInstance().addToSharedMem("resultframemirrored1" + String.valueOf(sessionID), String.valueOf(mCameraMirrored));
		
		
		PluginManager.getInstance().addToSharedMem("amountofresultframes"+Long.toString(sessionID), String.valueOf(1));
		
		PluginManager.getInstance().addToSharedMem("sessionID", String.valueOf(sessionID));
    }
    
    @Override
	public boolean handleMessage(Message msg)
	{
    	switch (msg.what)
    	{
    	case MSG_END_OF_LOADING:
			setupImageView();
			setupSaveButton();
			postProcessingRun = true;
    		break;
    	case MSG_SAVE:
    		try {
				mAlmaCLRShot.setObjectList(mObjStatus);
			} catch (Exception e) {
				e.printStackTrace();
			}
    		savePicture(MainScreen.getMainContext());
    		mHandler.sendEmptyMessage(MSG_LEAVING);
    		break;
    	case MSG_LEAVING:
    		if (released)
    			return false;
    		MainScreen.getMessageHandler().sendEmptyMessage(PluginManager.MSG_POSTPROCESSING_FINISHED);
    		mJpegBufferList.clear();
    		
    		Message msg2 = new Message();
    		msg2.arg1 = PluginManager.MSG_CONTROL_UNLOCKED;
    		msg2.what = PluginManager.MSG_BROADCAST;
    		MainScreen.getMessageHandler().sendMessage(msg2);
    		
    		MainScreen.getGUIManager().lockControls = false;   		

    		postProcessingRun = false;
    		
    		mAlmaCLRShot.release();
    		released=true;
        	break;
        	
    	case MSG_REDRAW:
            if (PreviewBmp != null)
            	PreviewBmp.recycle();
            if (finishing)
				return true;
    		PreviewBmp = mAlmaCLRShot.getPreviewBitmap();
    		drawObjectRectOnBitmap(PreviewBmp, mAlmaCLRShot.getObjectInfoList(), mAlmaCLRShot.getObjBorderBitmap(paint));
            if (PreviewBmp != null) 
        	{
            	Matrix matrix = new Matrix();
            	matrix.postRotate(90);
            	Bitmap rotated = Bitmap.createBitmap(PreviewBmp, 0, 0, PreviewBmp.getWidth(), PreviewBmp.getHeight(),
            	        matrix, true);
            	mImgView.setImageBitmap(rotated);
            	mImgView.setRotation(CameraController.isFrontCamera()? ((mDisplayOrientation == 0 || mDisplayOrientation == 180) ? 0 : 180) : 0);
        	}
            break;
    	default:
			break;
    	}    	
    	return true;
	}
	
	
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK && MainScreen.getInstance().findViewById(R.id.postprocessingLayout).getVisibility() == View.VISIBLE)
		{
			if (finishing)
				return true;
			finishing = true;
			mHandler.sendEmptyMessage(MSG_LEAVING);
			return true;
		}
		
		return false;
	}
		
	
	private void drawObjectRectOnBitmap(Bitmap bitmap, ObjectInfo[] objInfo, ObjBorderInfo[] boderInfo) {
		float ratio = 0.f;

		Paint paint = new Paint();
		paint.setColor(Color.rgb(0, 255, 0));
		paint.setStrokeWidth(6);
		paint.setAlpha(255);
		paint.setStyle(Paint.Style.STROKE);
		paint.setStrokeJoin(Paint.Join.ROUND);
		paint.setStrokeCap(Paint.Cap.ROUND);
		
		switch(mAngle) {
		case 0:
			ratio = (float)1.f;
			break;
		case 90:
			ratio = (float)bitmap.getWidth() / (float)bitmap.getHeight();			
			break;
		case 180:
			ratio = (float)1.f;
			break;
		case 270:
			ratio = (float)bitmap.getWidth() / (float)bitmap.getHeight();
			break;
		default:
			break;
		}

		Canvas c = new Canvas(bitmap);

		int i = 0;
		for (ObjectInfo obj : objInfo) {
			if (obj.getThumbnail() == null) {
				continue;
			}
			float left = (float)obj.getRect().left/ratio;
			float top = (float)obj.getRect().top/ratio;
			float right = (float)obj.getRect().right/ratio;
			float bottom =(float)obj.getRect().bottom/ratio;

			Rect newRect = new Rect((int)Math.round(left), (int)Math.round(top), (int)Math.round(right), (int)Math.round(bottom));

			if (!mObjStatus[i]) { 
				c.drawBitmap(obj.getThumbnail(), null, newRect, paint);
			}
			i++;
		}
		
		Paint  p;
		p = new Paint();
		
		for (ObjBorderInfo obj : boderInfo) {
			if (obj == null ) {
				continue;
			}
			if (obj.getThumbnail() == null) {
				continue;
			}

			Rect newRect = obj.getRect();
			
			c.drawBitmap(obj.getThumbnail(), null, newRect, p);
		}

		return;
	}
	
/************************************************
 * 		POST PROCESSING END
 ************************************************/

	private OnProcessingListener mOnProcessingListener = new OnProcessingListener() {

		@Override
		public void onObjectCreated(ObjectInfo objInfo) {
		}

		@Override
		public void onProcessingComplete(ObjectInfo[] objInfoList) {
		}
    	
    };
	
	private void getPrefs() {
		// Get the xml/preferences.xml preferences
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MainScreen.getInstance().getBaseContext());
        mSensitivity = prefs.getInt("Sensitivity", 19); //Should we manage this parameter or it's final value of 19?
        mMinSize = prefs.getInt("MinSize", 1000); //Should we manage this parameter or it's final value of 1000?
        mGhosting = prefs.getString("Ghosting", "2"); //Should we manage this parameter or it's final value of 2?
    }
}
