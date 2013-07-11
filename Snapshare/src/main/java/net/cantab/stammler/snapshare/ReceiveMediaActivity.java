package net.cantab.stammler.snapshare;

/**
 ReceiveMediaActivity.java created on 6/26/13.

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
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import static net.cantab.stammler.snapshare.Snapshare.LOG_TAG;

/**
 * This Activity has an intent-filter to receive images and videos.
 *
 * It basically functions as a wrapper around Snapchat. Upon creation, we double check, that actually
 * an image or video was passed to it and then call Snapchat's main launcher Activity,
 * com.snapchat.android.LandingPageActivity with the same intent.
 *
 * Now the remaining work is done in some hooked methods of Snapchat. Upon creation of the
 * LandingPageActivity, the injected code checks if the intent is a share intent and then does the
 * work necessary to let the image or video be shown.
 */
public class ReceiveMediaActivity extends Activity {
    @Override
    public void onCreate (Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();
        Uri mediaUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (type != null && Intent.ACTION_SEND.equals(action) && (type.startsWith("image/") || type.startsWith("video/"))) {
            if (mediaUri == null) {
                Log.d(LOG_TAG, "Media URI null!");
                return;
            }
            Log.d(LOG_TAG, "Received Media share of type " + type
                    + "\nand URI " + mediaUri.toString()
                    + "\nCalling hooked Snapchat with same Intent.");
            intent.setComponent(ComponentName.unflattenFromString("com.snapchat.android/.LandingPageActivity"));
            startActivity(intent);
        }
        //call finish at the end to close the wrapper
        finish();
    }
}
