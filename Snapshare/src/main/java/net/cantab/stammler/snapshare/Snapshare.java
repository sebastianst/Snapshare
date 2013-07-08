package net.cantab.stammler.snapshare;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
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
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.newInstance;

/**
 * Created by sebastian on 6/26/13.
 *
 */

public class Snapshare implements IXposedHookLoadPackage {
    public static final String LOG_TAG = "Snapshare";

    private Uri initializedUri;

    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.snapchat.android"))
            return;
        else
            XposedBridge.log("Snapshare: Snapchat load detected.");

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
                if (type != null && Intent.ACTION_SEND.equals(action) && type.startsWith("image/")) {
                    Uri imageUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                    if (imageUri == null) {
                        return;
                    }
                    /* We check if the current image got already initialized and should exit instead
                    of doing the bitmap initialization again. This check is necessary
                    because onCreate() is also called if the phone is just rotated. */
                    if (initializedUri == imageUri) {
                        Log.d(LOG_TAG, "Bitmap already created, exit onCreate()");
                        return;
                    }
                    //InputStream iStream;
                    try {
                        /*iStream = getContentResolver().openInputStream(imageUri);
                         oStream = new ByteArrayOutputStream(iStream.available());
                         Log.d("Snapshare", "iStream.available(): " + iStream.available());
                         int byteSize = IOUtils.copy(iStream, oStream);
                         Log.v("Snapshare", "Image size: " + byteSize/1024 + " kB");*/
                        /*TODO use BitmapFactory with inSampleSize magic to avoid using too much memory,
                         see http://developer.android.com/training/displaying-bitmaps/load-bitmap.html#load-bitmap */
                        Bitmap bitmap = MediaStore.Images.Media.getBitmap((ContentResolver) callSuperMethod(thiz, "getContentResolver"), imageUri);
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

                        /* Scaling and cropping mayhem

                        Snapchat will break if the image is too large and it will scale the image up if the
                        Display rectangle (DisplayMetrics.widthPixels x DisplayMetrics.heightPixels rectangle)
                        is larger than the image.

                        So, we sample the image down such that the Display rectangle fits into it and touches one side.
                        Then we crop the picture to that rectangle */
                        DisplayMetrics dm = new DisplayMetrics();
                        ((WindowManager) callSuperMethod(thiz, "getWindowManager")).getDefaultDisplay().getMetrics(dm);
                        int dWidth = dm.widthPixels;
                        int dHeight = dm.heightPixels;
                        Log.d(LOG_TAG, "Display metrics w x h: " + dWidth + " x " + dHeight);
                        // DisplayMetrics' values depend on the phone's tilt, so we normalize them to Portrait mode
                        if (dWidth > dHeight) {
                            Log.d(LOG_TAG, "Normalizing display metrics to Portrait mode.");
                            int temp = dWidth;
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

                        /* We fake a SnapCapturedEvent with our bitmap and call the onSnapCaptured
                        * method with this fake to let Snapchat display the image in the editor
                        * as if the image was just taken with the camera. */
                        Class SnapCapturedEventClass = Class.forName("com.snapchat.android.util.eventbus.SnapCapturedEvent", true, thiz.getClass().getClassLoader());
                        Object captureEvent = newInstance(SnapCapturedEventClass, bitmap);
                        callMethod(thiz, "onSnapCaptured", captureEvent);
                        /* Finally the image is marked as initialized to prevent re-calculation of
                        * the bitmap in case of a screen rotation (because onCreate() is then called) */
                        initializedUri = imageUri;
                    } catch (FileNotFoundException e) {
                        Log.e(LOG_TAG, "File not found!", e);
                    } catch (IOException e) {
                        Log.e(LOG_TAG, "IO Error!", e);
                    }
                }
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
                Bitmap bitmap = (Bitmap) getObjectField(thiz, "mImageBitmap");
                Log.d(LOG_TAG, "Fr> Image Width x Height: " + bitmap.getWidth() + " x " + bitmap.getHeight());
                DisplayMetrics dm = (DisplayMetrics) getObjectField(thiz, "mDisplayMetrics");
                Log.d(LOG_TAG, "Fr> SnapPreviewActivity Display Metrics w x h: " + dm.widthPixels + " x " + dm.heightPixels);
            }
        });
    }

    /*
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
