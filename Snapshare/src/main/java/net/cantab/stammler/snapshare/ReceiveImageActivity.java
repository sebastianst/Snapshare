package net.cantab.stammler.snapshare;

/**
 * Created by sebastian on 6/26/13.
 *
 */

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import static net.cantab.stammler.snapshare.Snapshare.LOG_TAG;

public class ReceiveImageActivity extends Activity {
    @Override
    public void onCreate (Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();
        Uri imageUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (type != null && Intent.ACTION_SEND.equals(action) && type.startsWith("image/")) {
            if (imageUri == null) {
                Log.d(LOG_TAG, "Image URI null!");
                return;
            }
            Log.d(LOG_TAG, "Received Image share of type " + type
                    + "\nand URI " + imageUri.toString()
                    + "\nCalling hooked Snapchat with same Intent.");
            intent.setComponent(ComponentName.unflattenFromString("com.snapchat.android/.LandingPageActivity"));
            startActivity(intent);
        }
        //call finish at the end to close the wrapper
        finish();
    }
}
//TODO video sharing:
// intent.putExtra("videoFile", imageUri.toString()); LATER for video share. should be a file:// string, pointing to a .mp4 file
