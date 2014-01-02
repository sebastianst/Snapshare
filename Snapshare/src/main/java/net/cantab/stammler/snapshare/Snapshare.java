package net.cantab.stammler.snapshare;

/**
 Snapshare.java created on 6/26/13.

 Copyright (C) 2013 Sebastian Stammler <stammler@cantab.net>

 This file is part of Snapshare.

 Snapshare is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Snapshare is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 a gazillion times. If not, see <http://www.gnu.org/licenses/>.
 */

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.WindowManager;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import com.amcgavin.snapshare.Media;
import com.amcgavin.snapshare.Obfuscator;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static android.graphics.Bitmap.createBitmap;
import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getBooleanField;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.newInstance;
import static de.robv.android.xposed.XposedHelpers.setStaticBooleanField;
import static de.robv.android.xposed.XposedHelpers.callStaticMethod;
import static de.robv.android.xposed.XposedHelpers.setObjectField;

public class Snapshare implements IXposedHookLoadPackage {
    // Debugging settings
    public static final String LOG_TAG = "Snapshare";
    public static final boolean DEBUG = false;
    /** Enable Snapchat's internal debugging mode? */
    public static final boolean TIMBER = false;

    /** what version is snapchat? */
    public static int SNAPCHAT_VERSION = Obfuscator.FOUR_22;

    /** Adjustment methods */
    private int adjustMethod;
    public static final int ADJUST_CROP = 0;
    public static final int ADJUST_SCALE = 1;
    public static final int ADJUST_NONE = 2;

    /** Only if a video file path contains this pattern, Snapchat is allowed to delete the video,
     * because then it is a video file recored by Snapchat itself and not a shared one. */
    public static final String VIDEO_CACHE_PATTERN = "/com.snapchat.android/cache/sending_video_snaps/snapchat_video";
    /** After calling initSnapPreviewFragment() below, we set the
     * initializedUri to the current media's Uri to prevent another call of onCreate() to initialize
     * the media again. E.g. onCreate() is called again if the phone is rotated. */
    private Uri initializedUri;
    private XSharedPreferences prefs = new XSharedPreferences("net.cantab.stammler.snapshare");
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.snapchat.android"))
            return;
        else
            XposedBridge.log("Snapshare: Snapchat load detected.");

        refreshPrefs();

        /** thanks to KeepChat for the following snippet: **/
        Object activityThread = callStaticMethod(
                findClass("android.app.ActivityThread", null), "currentActivityThread");
        Context context = (Context) callMethod(activityThread, "getSystemContext");
        int version = context.getPackageManager().getPackageInfo(lpparam.packageName, 0).versionCode;
        Log.d(LOG_TAG, "Version code: " + version);
        if(version < 175) {
            SNAPCHAT_VERSION = Obfuscator.FOUR_20;
        }
        else if(version == 175) {
            SNAPCHAT_VERSION = Obfuscator.FOUR_21;
        }
        else if(version == 181) {
            SNAPCHAT_VERSION = Obfuscator.FOUR_22;
        }


        // Timber is Snapchat's internal debugging class. By default, it is disabled in the upstream
        // Snapchat version. We can enable it by settings its static member DEBUG to true.
        if (TIMBER) {
            Log.d(LOG_TAG, "Enabling Snapchat Timber debugging messages.");
            setStaticBooleanField(findClass("com.snapchat.android.Timber", lpparam.classLoader), "DEBUG", true);
            // The Snapchat devs screwed up their own debugging. Without this hook, Snapchat force
            // closes upon launching because erroneous String.format() calls are made.
            findAndHookMethod("com.snapchat.android.Timber", lpparam.classLoader, "d", String.class, Object[].class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    String str = (String) param.args[0];
                    Object [] obja = (Object []) param.args[1];
                    try {
                        String msg = String.format(str, obja);
                        Log.d(LOG_TAG, "Timber: " + msg);
                    } catch(java.util.IllegalFormatConversionException e) {
                        Log.w(LOG_TAG, "Timber tried to format: " + str + " -- Snapchat screwed up their own debugging - doh!");
                        param.setResult(null);
                    }
                }
            });
        }

        final Class SnapCapturedEventClass = findClass("com.snapchat.android.util.eventbus.SnapCapturedEvent", lpparam.classLoader);
        final Media media = new Media(); // a place to store the media



        /**
         * Here the main work happens. We hook after the onCreate() call of the main Activity
         * to create a sensible media object.
         */
        findAndHookMethod("com.snapchat.android.LandingPageActivity", lpparam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                refreshPrefs();
                Object thiz = param.thisObject;
                // Get intent, action and MIME type
                Intent intent = (Intent) callSuperMethod(thiz, "getIntent");
                String type = intent.getType();
                String action = intent.getAction();
                Log.d(LOG_TAG, "intent type: " + type + ", intent action:" + action);
                // Check if this is a normal launch of Snapchat or actually called by Snapshare
                if (type != null && Intent.ACTION_SEND.equals(action)) {
                    Uri mediaUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                    // Check for bogus call
                    if (mediaUri == null) {
                        return;
                    }
                    /* We check if the current media got already initialized and should exit instead
                     * of doing the media initialization again. This check is necessary
                     * because onCreate() is also called if the phone is just rotated. */
                    if (initializedUri == mediaUri) {
                        Log.d(LOG_TAG, "Media already initialized, exit onCreate() hook");
                        return;
                    }
                    ContentResolver thizContentResolver = (ContentResolver) callSuperMethod(thiz, "getContentResolver");
                    if (type.startsWith("image/")) {

                        //InputStream iStream;
                        try {
                            /*iStream = getContentResolver().openInputStream(mediaUri);
                         oStream = new ByteArrayOutputStream(iStream.available());
                         Log.d("Snapshare", "iStream.available(): " + iStream.available());
                         int byteSize = IOUtils.copy(iStream, oStream);
                         Log.v("Snapshare", "Image size: " + byteSize/1024 + " kB");*/
                            /*TODO use BitmapFactory with inSampleSize magic to avoid using too much memory,
                         see http://developer.android.com/training/displaying-bitmaps/load-bitmap.html#load-bitmap */
                            Bitmap bitmap = MediaStore.Images.Media.getBitmap(thizContentResolver, mediaUri);
                            int width = bitmap.getWidth();
                            int height = bitmap.getHeight();
                            Log.d(LOG_TAG, "Original image w x h: " + width + " x " + height);
                            // Landscape images have to be rotated 90 degrees clockwise for Snapchat to be displayed correctly
                            if (width > height) {
                                // check if the user wants to rotate
                                if(prefs.getBoolean("pref_key_rotate", true)) {
                                    Log.d(LOG_TAG, "Landscape image detected, rotating 90 degrees clockwise.");
                                    Matrix matrix = new Matrix();
                                    matrix.setRotate(90);
                                    bitmap = createBitmap(bitmap, 0, 0, width, height, matrix, true);
                                    // resetting width and height
                                    width = bitmap.getWidth();
                                    height = bitmap.getHeight();
                                }
                            }

                            /**
                             * Scaling and cropping mayhem
                             *
                             * Snapchat will break if the image is too large and it will scale the image up if the
                             * Display rectangle (DisplayMetrics.widthPixels x DisplayMetrics.heightPixels rectangle)
                             * is larger than the image.
                             *
                             * So, we sample the image down such that the Display rectangle fits into it and touches one side.
                             * Then we crop the picture to that rectangle
                             */
                            DisplayMetrics dm = new DisplayMetrics();
                            ((WindowManager) callSuperMethod(thiz, "getWindowManager")).getDefaultDisplay().getMetrics(dm);
                            int dWidth = dm.widthPixels;
                            int dHeight = dm.heightPixels;

                            Log.d(LOG_TAG, "Display metrics w x h: " + dWidth + " x " + dHeight);
                            // DisplayMetrics' values depend on the phone's tilt, so we normalize them to Portrait mode
                            if (dWidth > dHeight) {
                                Log.d(LOG_TAG, "Normalizing display metrics to Portrait mode.");
                                int temp = dWidth;
                                //noinspection SuspiciousNameCombination
                                dWidth = dHeight;
                                dHeight = temp;
                            }
                            if(adjustMethod==ADJUST_CROP) {
                                /* If the image properly covers the Display rectangle, we mark it as a "large" image
                            and are going to scale it down. We make this distinction because we don't wanna
                            scale the image up if it is smaller than the Display rectangle. */
                                boolean largeImage = ((width > dWidth) & (height > dHeight));
                                Log.d(LOG_TAG, "Large image? " + largeImage);
                                int imageToDisplayRatio = width * dHeight - height * dWidth;
                                if (imageToDisplayRatio > 0) {
                                    // i.e., width/height > dWidth/dHeight, so have to crop from left and right:
                                    int newWidth = (dWidth * height / dHeight);
                                    Log.d(LOG_TAG, "New width after cropping left & right: " + newWidth);
                                    bitmap = createBitmap(bitmap, (width - newWidth) / 2, 0, newWidth, height);

                                } else if (imageToDisplayRatio < 0) {
                                    // i.e., width/height < dWidth/dHeight, so have to crop from top and bottom:
                                    int newHeight = (dHeight * width / dWidth);
                                    Log.d(LOG_TAG, "New height after cropping top & bottom: " + newHeight);
                                    bitmap = createBitmap(bitmap, 0, (height - newHeight) / 2, width, newHeight);
                                }
                                if (largeImage) {
                                    Log.d(LOG_TAG, "Scaling down.");
                                    bitmap = Bitmap.createScaledBitmap(bitmap, dWidth, dHeight, true);
                                }
                                /// Scaling and cropping finished, ready to let Snapchat display our result
                            }
                            else {
                                // we are going to scale the image down and place a black background behind it
                                Bitmap background = Bitmap.createBitmap(dWidth, dHeight, Bitmap.Config.ARGB_8888);
                                background.eraseColor(Color.BLACK);
                                Canvas canvas = new Canvas(background);
                                Matrix transform = new Matrix();
                                float scale = dWidth / (float)width;
                                float xTrans = 0;
                                if(adjustMethod == ADJUST_NONE) {
                                    // Remove scaling and add some translation
                                    scale = 1;
                                    xTrans = dWidth/2 - width/2;
                                }
                                float yTrans = dHeight/2 - scale*height/2;

                                transform.preScale(scale, scale);
                                transform.postTranslate(xTrans, yTrans);
                                Paint paint = new Paint();
                                paint.setFilterBitmap(true);
                                canvas.drawBitmap(bitmap, transform, paint);
                                bitmap = background;
                            }

                            // Make Snapchat show the image
                            media.setContent(bitmap);
                        } catch (FileNotFoundException e) {
                            Log.w(LOG_TAG, "File not found!", e);
                        } catch (IOException e) {
                            Log.e(LOG_TAG, "IO Error!", e);
                        }
                    }
                    else if (type.startsWith("video/")) {
                        // Snapchat expects the video URI to be in the file:// format, not content://
                        // so we have to convert the URI
                        String [] proj = {MediaStore.Images.Media.DATA};
                        Cursor cursor = thizContentResolver.query(mediaUri, proj, null, null, null);
                        if (cursor != null) {
                            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                            cursor.moveToFirst();
                            String filePath = cursor.getString(column_index);
                            Log.d(LOG_TAG, "Converted content URI " + mediaUri.toString() + " to file path " + filePath);
                            cursor.close();
                            File videoFile = new File(filePath);
                            // Make Snapchat show the video
                            media.setContent(Uri.fromFile(videoFile));
                        } else {
                            Log.w(LOG_TAG, "Couldn't resolve content URI to file path!");
                        }
                    }
                    /* Finally the image or video is marked as initialized to prevent reinitialisation of
                     * the SnapCapturedEvent in case of a screen rotation (because onCreate() is then called).
                     * This way, it is made sure that a shared image or media is only initialized and then
                     * showed in a SnapPreviewFragment once.
                     * Also, if Snapchat is used normally after being launched by Snapshare, a screen rotation
                     * while in the SnapPreviewFragment, would draw the shared image or video instead of showing
                     * what has just been recorded by the camera. */
                    initializedUri = mediaUri;
                }
                else {
                    Log.d(LOG_TAG, "Normal call of Snapchat.");
                    initializedUri = null;
                }
            }

        });
        /** 
         * We needed to find a method that got called after the camera was ready. 
         * refreshFlashButton is the only method that falls under this category.
         * As a result, we need to be very careful to clean up after ourselves, to prevent
         * crashes and not being able to quit etc...
         * 
         * after the method is called, we call the eventbus to send a snapcapture event 
         * with our own media.
         */
        findAndHookMethod("com.snapchat.android.camera.CameraPreviewFragment", lpparam.classLoader, Obfuscator.CAMERA_LOAD.getValue(SNAPCHAT_VERSION), new XC_MethodHook() {

            @Override
            protected void afterHookedMethod(MethodHookParam param)
                    throws Throwable {
                if(initializedUri == null) return; // We don't have an image to send, so don't try to send one
                Object snapCaptureEvent = newInstance(SnapCapturedEventClass, media.getContent());
                callMethod(callStaticMethod(findClass("com.snapchat.android.util.eventbus.BusProvider", lpparam.classLoader), Obfuscator.GET_BUS.getValue(SNAPCHAT_VERSION))
                        , Obfuscator.BUS_POST.getValue(SNAPCHAT_VERSION), snapCaptureEvent);

                initializedUri = null; // clean up after ourselves. If we don't do this snapchat will crash.

            }
        });

        // This is a pure debugging hook.
        if (DEBUG)
            findAndHookMethod("com.snapchat.android.LandingPageActivity", lpparam.classLoader, "onSnapCapturedEvent", SnapCapturedEventClass, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    // boolean b = (Boolean) callMethod(param.thisObject, "isSnapPreviewFragmentShowing");
                    Bitmap bitmap = (Bitmap) callMethod(param.args[0], "getPhotoBitmap");
                    Log.d(LOG_TAG, "snapCapturedEvent's bitmap trivial? " + (bitmap == null));
                }
            });

        // This is a pure debugging hook to print some information about the intent and image.
        if (DEBUG)
            findAndHookMethod("com.snapchat.android.SnapPreviewFragment", lpparam.classLoader, "onCreateView", LayoutInflater.class, ViewGroup.class, Bundle.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Log.d(LOG_TAG, "Fr#onCreateView> mSnapCapturedEvent null? " + (getObjectField(param.thisObject, "mSnapCapturedEvent") == null));
                    Object sce = callMethod(callSuperMethod(param.thisObject, "getActivity"), "getSnapCapturedEvent");
                    Log.d(LOG_TAG, "Fr#onCreateView> getSnapCapturedEvent returns null? " + (sce == null));
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Object thiz = param.thisObject;
                    Activity curActivity = (Activity) callSuperMethod(thiz, "getActivity");
                    Log.d(LOG_TAG, "Fr#onCreateView> Current Activity: " + curActivity.getLocalClassName());
                    Intent intent = curActivity.getIntent();
                    Log.d(LOG_TAG, "Fr#onCreateView> Intent type: " + intent.getType());
                    if (getBooleanField(thiz, "mIsVideoSnap")) {
                        Log.d(LOG_TAG, "Fr#onCreateView> Previewing a video.");
                    }
                    else {
                        Bitmap bitmap = (Bitmap) getObjectField(thiz, "mImageBitmap");
                        Log.d(LOG_TAG, "Fr#onCreateView> mImageBitmap null? " + (bitmap == null));
                        if (bitmap != null) {
                            Log.d(LOG_TAG, "Fr#onCreateView> Image Width x Height: " + bitmap.getWidth() + " x " + bitmap.getHeight());
                            DisplayMetrics dm = (DisplayMetrics) getObjectField(thiz, "mDisplayMetrics");
                            Log.d(LOG_TAG, "Fr#onCreateView> SnapPreviewActivity Display Metrics w x h: " + dm.widthPixels + " x " + dm.heightPixels);
                        }
                    }
                }
            });

        /** The following two hooks prevent Snapchat from deleting videos shared with Snapshare.
         * It does so by checking whether the path of the video file to be deleted contains the
         * VIDEO_CACHE_PATTERN, which then would imply that the video file resides in the Snapchat
         * cache and can thus be deleted. Otherwise, Snapchat is prevented from deleting the file.
         *
         * We could just copy the video into the temporary video cache of Snapchat and then don't
         * care that Snapchat is deleting videos after sending them. I found it, however, more fancy
         * to intercept all methods that delete the video files, in case we are sending our own video. ;)

        findAndHookMethod("com.snapchat.android.model.SentSnap", lpparam.classLoader, "deleteBackingVideoFile", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                String videoPath = (String) getObjectField(param.thisObject, "mSnapUriString");
                String logMsg;
                if (videoPath.contains(VIDEO_CACHE_PATTERN)) {
                    logMsg = "SS#deleteBackingVideoFile> Allow Snapchat to delete own cached video file ";
                } else {
                    param.setResult(null);
                    logMsg = "SS#deleteBackingVideoFile> Prevented Snapchat from deleting our video file ";
                }
                Log.d(LOG_TAG, logMsg + videoPath);
            }
        });
         */

        /**
         * Stop snapchat deleting our video when the view is cancelled.
         */
        findAndHookMethod("com.snapchat.android.SnapPreviewFragment", lpparam.classLoader, Obfuscator.ON_BACK_PRESS.getValue(SNAPCHAT_VERSION), new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Object thiz = param.thisObject;
                Object event = newInstance(SnapCapturedEventClass, Uri.fromFile(File.createTempFile("delete", "me")));
                setObjectField(thiz, Obfuscator.M_SNAP_C_EVENT.getValue(SNAPCHAT_VERSION), event);
                Log.d(LOG_TAG, "prevented snapchat from deleting our video.");
            }
        });

    }

    /** 
     * refreshes Preferences
     */
    private void refreshPrefs() {
        prefs.reload();
        adjustMethod = Integer.parseInt(prefs.getString("pref_key_adjustment", Integer.toString(ADJUST_CROP)));
    }
    /** {@code XposedHelpers.callMethod()} cannot call methods of the super class of an object, because it
     * uses {@code getDeclaredMethods()}. So we have to implement this little helper, which should work
     * similar to {@code }callMethod()}. Furthermore, the exceptions from getMethod() are passed on.
     * <p>
     * At the moment, only argument-free methods supported (only case needed here). After a discussion
     * with the Xposed author it looks as if the functionality to call super methods will be implemented
     * in {@code XposedHelpers.callMethod()} in a future release.
     *
     * @param obj Object whose method should be called
     * @param methodName String representing the name of the argument-free method to be called
     * @return The object that the method call returns
     * @see <a href="http://forum.xda-developers.com/showpost.php?p=42598280&postcount=1753">
     *     Discussion about calls to super methods in Xposed's XDA thread</a>
     */
    private Object callSuperMethod(Object obj, String methodName) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        return obj.getClass().getMethod(methodName).invoke(obj);
    }
}
