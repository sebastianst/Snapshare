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
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Matrix;
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

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static android.graphics.Bitmap.createBitmap;
import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.getBooleanField;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.newInstance;

public class Snapshare implements IXposedHookLoadPackage {
    public static final String LOG_TAG = "Snapshare";

    /**
     * We define pairs of classes and their void methods, which potentially delete video files.
     * Later, we intercept calls to these, check if we are actually sharing an external video,
     * instead of one captured with the camera, and prevent deletion in this case.
     */
    private static final short CLASS = 0;
    private static final short METHOD = 1;
    private static final String [][] VIDEO_DELETE_METHODS = {
            {"com.snapchat.android.SnapPreviewFragment","deleteVideoFileIfSnapIsVideo"},
            {"com.snapchat.android.model.SentSnap", "deleteBackingVideoFile"}
    };

    /**
     * After creating a SnapCapturedEvent and passing it to onSnapCaptured(), we set the
     * initializedUri to the current media's Uri, because onCreate() is called again upon phone rotation.
     * We furthermore set isOwnVideoSnap to true if we are sharing an external video to prevent deletion.
     */
    private Uri initializedUri;
    private boolean isOwnVideoSnap = false;

    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.snapchat.android"))
            return;
        else
            XposedBridge.log("Snapshare: Snapchat load detected.");

        final Class SnapCapturedEventClass = Class.forName("com.snapchat.android.util.eventbus.SnapCapturedEvent", true, lpparam.classLoader);

        /**
         * Here the main work happens. We hook after the onCreate() call of the main Activity
         * to create a sensible SnapCapturedEvent which is passed to onSnapCaptured(), which causes
         * Snapchat to load the SnapPreviewFragment, previewing our image or video.
         */
        findAndHookMethod("com.snapchat.android.LandingPageActivity", lpparam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Object thiz = param.thisObject;
                // Get intent, action and MIME type
                Intent intent = (Intent) callSuperMethod(thiz, "getIntent");
                String type = intent.getType();
                String action = intent.getAction();
                Log.d(LOG_TAG, "intent type: " + type + ", intent action:" + action);
                // Check if this is a normal launch of Snapchat or actually called by Snapshare
                if (type != null && Intent.ACTION_SEND.equals(action)) {
                    Uri mediaUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                    if (mediaUri == null) {
                        return;
                    }
                    if (initializedUri == mediaUri) {
                        Log.d(LOG_TAG, "SnapCapturedEvent already created, exit onCreate()");
                        return;
                    }
                    ContentResolver thizContentResolver = (ContentResolver) callSuperMethod(thiz, "getContentResolver");
                    if (type.startsWith("image/")) {
                        /* We check if the current image got already initialized and should exit instead
                        of doing the bitmap initialization again. This check is necessary
                        because onCreate() is also called if the phone is just rotated. */
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
                                Log.d(LOG_TAG, "Landscape image detected, rotating 90 degrees clockwise.");
                                Matrix matrix = new Matrix();
                                matrix.setRotate(90);
                                bitmap = createBitmap(bitmap, 0, 0, width, height, matrix, true);
                                // resetting width and height
                                width = bitmap.getWidth();
                                height = bitmap.getHeight();
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

                            /** We fake a SnapCapturedEvent with our bitmap and call the onSnapCaptured
                             * method with this fake to let Snapchat display the image in the editor
                             * as if the image was just taken with the camera. */
                            Object captureEvent = newInstance(SnapCapturedEventClass, bitmap);
                            callMethod(thiz, "onSnapCaptured", captureEvent);
                        } catch (FileNotFoundException e) {
                            Log.w(LOG_TAG, "File not found!", e);
                        } catch (IOException e) {
                            Log.e(LOG_TAG, "IO Error!", e);
                        }
                    }
                    else if (type.startsWith("video/")) {
                        /* Snapchat expects the video URI to be in the file:// format, not content://
                        * so we have to convert the URI */
                        String [] proj = {MediaStore.Images.Media.DATA};
                        Cursor cursor = thizContentResolver.query(mediaUri, proj, null, null, null);
                        if (cursor != null) {
                            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                            cursor.moveToFirst();
                            String filePath = cursor.getString(column_index);
                            Log.d(LOG_TAG, "Converted content URI " + mediaUri.toString() + " to file path " + filePath);
                            cursor.close();
                            File videoFile = new File(filePath);
                            /** We fake a SnapCapturedEvent with the video URI and call the onSnapCaptured
                            * method with this fake to let Snapchat display the video
                            * as if the video was just taken with the camera. */
                            Object captureEvent = newInstance(SnapCapturedEventClass, Uri.fromFile(videoFile));
                            callMethod(thiz, "onSnapCaptured", captureEvent);
                            // set marker that we inject a video into Snapchat, so it doesn't get deleted
                            isOwnVideoSnap = true;
                        } else {
                            Log.w(LOG_TAG, "Couldn't resolve content URI to file path!");
                        }
                    }
                    /* Finally the image or video is marked as initialized to prevent reinitialisation of
                     * the SnapCapturedEvent in case of a screen rotation (because onCreate() is then called) */
                    initializedUri = mediaUri;
                }
                else {
                    Log.d(LOG_TAG, "Normal call of Snapchat.");
                    isOwnVideoSnap = false;
                    initializedUri = null;
                }
            }
        });

        /**
         * We reset the local variables which indicate, that the image or video comes from Snapshare.
         *
         * If we change to the camera after we have shared an image or video with Snapshare,
         * onCreate() of the main Activity doesn't get called. Thus, Snapshare will still prevent
         * Snapchat from deleting sent videos (as isOwnVideoSnap was set true before), but it should
         * do so with videos recorded within Snapchat.
         *
         * Also, if we loaded the same image with Snapshare. after such an action, the
         * initializedUri would still point to it and the above code would not run, so we reset this
         * parameter as well.
         *
         * That's also the reason we have to set the initializedUri to the actual uri and
         * isOwnVideoSnap to true *after* the calls to onSnapCaptured() above.
         */
        findAndHookMethod("com.snapchat.android.LandingPageActivity", lpparam.classLoader, "onSnapCaptured", SnapCapturedEventClass, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                isOwnVideoSnap = false;
                initializedUri = null;
            }
        });

        // This is a pure debugging hook to print some information about the intent and image.
        findAndHookMethod("com.snapchat.android.SnapPreviewFragment", lpparam.classLoader, "onCreateView", LayoutInflater.class, ViewGroup.class, Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Object thiz = param.thisObject;
                Activity curActivity = (Activity) callSuperMethod(thiz, "getActivity");
                Log.d(LOG_TAG, "Fr> Current Activity: " + curActivity.getLocalClassName());
                Intent intent = curActivity.getIntent();
                Log.d(LOG_TAG, "Fr> Intent type: " + intent.getType());
                if (getBooleanField(thiz, "mIsVideoSnap")) {
                    Log.d(LOG_TAG, "Fr> Previewing a video.");
                }
                else {
                    Bitmap bitmap = (Bitmap) getObjectField(thiz, "mImageBitmap");
                    Log.d(LOG_TAG, "Fr> Image Width x Height: " + bitmap.getWidth() + " x " + bitmap.getHeight());
                    DisplayMetrics dm = (DisplayMetrics) getObjectField(thiz, "mDisplayMetrics");
                    Log.d(LOG_TAG, "Fr> SnapPreviewActivity Display Metrics w x h: " + dm.widthPixels + " x " + dm.heightPixels);
                }
            }
        });

        /**
         * We could just copy the video into the temporary video directory of Snapchat and then don't
         * care that Snapchat is deleting videos after sending them. I found it, however, more fancy
         * to intercept all methods that delete the video files, in case we are sending our own video.
         * We probably don't want them to be deleted ;)
         */
        for (String [] deleteClassMethod : VIDEO_DELETE_METHODS)
            findAndHookMethod(deleteClassMethod[CLASS], lpparam.classLoader, deleteClassMethod[METHOD], new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Log.d(LOG_TAG, param.thisObject.getClass().getName() + "#" + param.method.getName() + " method called.");
                    if (isOwnVideoSnap) {
                        Log.d(LOG_TAG, "Prevent Snapchat from deleting our video.");
                        param.setResult(null);
                    } else
                        Log.d(LOG_TAG, "Allow Snapchat to delete the video file.");
                }
            });
    }

    /**
     * callSuperMethod()
     *
     * XposedHelpers.callMethod() cannot call methods of the super class of an object, because it
     * uses getDeclaredMethods(). So we have to implement this little helper, which should work
     * similar to callMethod(). Furthermore, the exceptions from getMethod() are passed on.
     * See http://forum.xda-developers.com/showpost.php?p=42598280&postcount=1753
     */
    private Object callSuperMethod(Object obj, String methodName) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        return obj.getClass().getMethod(methodName).invoke(obj);
    }
}
