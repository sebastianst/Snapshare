package net.cantab.stammler.snapshare;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import java.lang.reflect.InvocationTargetException;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

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
    
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.snapchat.android"))
            return;
        else
            XposedBridge.log("Snapshare: Snapchat load detected.");

        findAndHookMethod("com.snapchat.android.LandingPageActivity", lpparam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Object thiz = param.thisObject;
                //Log.i(LOG_TAG, "Invoking super.onCreate()");
                //Activity.class.getMethod("onCreate", Bundle.class).invoke(thiz, param.args[0]);
                Intent intent = (Intent) callSuperMethod(thiz, "getIntent");
                String type = intent.getType();
                Log.d(LOG_TAG, "intent type: " + type);
                //Log.i(LOG_TAG, "param.args[0] toString():" + ((Bundle) param.args[0]).toString());
                /*if (type != null) {
                    if (type.startsWith("image/")) {
                        intent.setComponent(ComponentName.unflattenFromString("com.snapchat.android/.SnapPreviewActivity"));
                        //callSuperMethod(thiz, "startActivity", intent);
                        thiz.getClass().getMethod("startActivity", Intent.class).invoke(thiz, intent);
                        callSuperMethod(thiz, "finish");
                    }
                }*/
                int dataLength = intent.getIntExtra("dataLength", -1);
                Log.d(LOG_TAG, "data length: " + dataLength);
                if (dataLength > 0) {
                    Bitmap imageBitmap = BitmapFactory.decodeByteArray(intent.getByteArrayExtra("imageData"), 0, dataLength);
                    Class SnapCapturedEventClass = Class.forName("com.snapchat.android.util.eventbus.SnapCapturedEvent", true, thiz.getClass().getClassLoader());
                    Object captureEvent = newInstance(SnapCapturedEventClass, imageBitmap);
                    callMethod(thiz, "onSnapCaptured", captureEvent);
                }
                //callSuperMethod(thiz, "finish");
            }
        });
        findAndHookMethod("com.snapchat.android.SnapPreviewFragment", lpparam.classLoader, "onCreateView", LayoutInflater.class, ViewGroup.class, Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Object thiz = param.thisObject;
                Activity curActivity = (Activity) callSuperMethod(thiz, "getActivity");
                Log.d(LOG_TAG, "Fr> Current Activity: " + curActivity.getLocalClassName());
                Intent intent = curActivity.getIntent();
                Log.d(LOG_TAG, "Fr> Intent type: " + intent.getType());
                Bitmap bitmap = (Bitmap) getObjectField(thiz, "mImageBitmap");
                Log.d(LOG_TAG, "Fr> Width: " + bitmap.getWidth() + ", Height: " + bitmap.getHeight());
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
