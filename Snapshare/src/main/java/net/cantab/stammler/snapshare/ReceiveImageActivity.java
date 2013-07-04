package net.cantab.stammler.snapshare;

/**
 * Created by sebastian on 6/26/13.
 *
 */

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import static android.graphics.Bitmap.createBitmap;

import static net.cantab.stammler.snapshare.Snapshare.LOG_TAG;

public class ReceiveImageActivity extends Activity {
    private static final int JPEG_QUALITY = 85;

    @Override
    public void onCreate (Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Get intent, action and MIME type
        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();
        Uri imageUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (Intent.ACTION_SEND.equals(action) && type.startsWith("image/")) {
            Log.d(LOG_TAG, "Received Image share of type " + type
                               + "\nand URI " + imageUri.toString()
                               + "\nCalling hooked Snapchat with same Intent.");

            //InputStream iStream;
            ByteArrayOutputStream oStream = new ByteArrayOutputStream();
            try {
                /*iStream = getContentResolver().openInputStream(imageUri);
                oStream = new ByteArrayOutputStream(iStream.available());
                Log.d("Snapshare", "iStream.available(): " + iStream.available());
                int byteSize = IOUtils.copy(iStream, oStream);
                Log.v("Snapshare", "Image size: " + byteSize/1024 + " kB");*/
                /*TODO use BitmapFactory with inSampleSize magic to avoid using too much memory,
                  see http://developer.android.com/training/displaying-bitmaps/load-bitmap.html#load-bitmap */
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);
                int width = bitmap.getWidth();
                int height = bitmap.getHeight();
                Log.d(LOG_TAG, "Original image w x h: " + width + " x " + height);
                // Portrait images have to be rotated 90 degrees clockwise for Snapchat to be displayed correctly
                if (height > width) {
                    Log.d(LOG_TAG, "Portrait image detected, rotating 90 degrees clockwise.");
                    Matrix matrix = new Matrix();
                    matrix.setRotate(270);
                    bitmap = createBitmap(bitmap, 0, 0, width, height, matrix, true);
                    // resetting width and height
                    width = bitmap.getWidth();
                    height = bitmap.getHeight();
                }

                /* Scaling and cropping mayhem

                Snapchat will break if the image sent does not fit into the
                DisplayMetrics.widthPixels x DisplayMetrics.heightPixels rectangle (Display rectangle)
                and it will scale the image up if the Display rectangle is larger than the image,
                ignoring the image's ratio.

                So, we sample the image down such that the Display rectangle fits into it and touches one side.
                Then we crop the picture to that rectangle */
                DisplayMetrics dm = new DisplayMetrics();
                getWindowManager().getDefaultDisplay().getMetrics(dm);
                int dWidth = dm.widthPixels;
                int dHeight = dm.heightPixels;
                Log.d(LOG_TAG, "Display metrics w x h: " + dWidth + " x " + dHeight);
                // DisplayMetrics' values depend on the phone's tilt, so we normalize them to landscape mode
                if (dHeight > dWidth) {
                    Log.d(LOG_TAG, "Normalizing display metrics to landscape mode.");
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
                    bitmap = createBitmap(bitmap, (width  - newWidth) / 2, 0, newWidth, height);

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
                /// Scaling and cropping finished, ready to pass to Snapchat

                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, oStream);
                Log.d(LOG_TAG, "outputStream size: " + oStream.size()/1024 + " kB = " + oStream.size() + " B");
                byte [] imageData = oStream.toByteArray();
                int dataLength = imageData.length;
                Log.d(LOG_TAG, "byte array size: " + dataLength/1024 + " kB = " + dataLength + " B");
                bitmap.recycle();
                intent.putExtra("imageData", imageData);
                intent.putExtra("dataLength", dataLength);
                intent.setComponent(ComponentName.unflattenFromString("com.snapchat.android/.LandingPageActivity"));
                startActivity(intent);
            } catch (FileNotFoundException e) {
                Log.e(LOG_TAG, "File not found!", e);
            } catch (IOException e) {
                Log.e(LOG_TAG, "IO Error!", e);
            }
        }
        //call finish at the end to close the wrapper
        finish();
    }
}
//TODO video sharing:
// intent.putExtra("videoFile", imageUri.toString()); LATER for video share. should be a file:// string, pointing to a .mp4 file
