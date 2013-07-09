Snapshare
=========
Introduction
------------
This programme lets you share any image via Snapchat, not only pictures you take with the camera
from within Snapchat. When you are viewing an image and click on *share*, Snapshare will pop up as
an option. It will load Snapchat and lets you even paint on the image as if you had just taken it
with your camera from within Snapchat.

It uses the [Xposed framework](http://forum.xda-developers.com/showthread.php?t=1574401) to
intercept launches of Snapchat from Snapshare and do the magic.

A compiled APK can be downloaded from my [Bintray site](https://bintray.com/sebastianst/Android/Snapshare).

The software was created using Android Studio with gradle on an Arch Linux machine.

Quick Installation
------------------
*Note: Obviously, you need __root access__ on your phone.*

1. Download the Xposed Installer APK from the above XDA thread
2. Install the app, run it, let it install the framework
3. Download and install my APK from the above Bintray link
4. Activate it in the Xposed Installer app
5. Soft reboot

To Do
-----
* Video sharing. I think this is actually easier than image sharing, because internally Snapchat also
  works with URI's to videos, instead of Bitmap Objects, like in the case of images.
* Adding Borders to the image, instead of cropping it, to fit the display area's size.
  Especially if you want to share, e.g. square Instagram images, this is probably preferable.

Troubleshooting/Bugs
--------------------
It happened to me a few times, that after loading an image into Snapchat with Snapshare, clicking the
*Send Button* didn't have any affect. I couldn't reproduce this bug in my latest testing though, so
 please let me know if this happens to you.

If you encounter any problems or bugs with this mod, please open logcat like this

    adb logcat -v time ActivityManager:I Snapshare:D "*:S"

then reproduce the error and report the *relevant* output (That's why I choose to display the time in
logcat's output, I don't wanna have your logcat of the last few days ;) ).

The code still contains many debugging `Log.d()` statements. I might remove them, at least from
releases, in the future, when the mod seems to work flawlessly on different devices and roms.

Technical Details
-----------------
Snapshare introduces an Activity which presents itself to the Android system as a receiver of images.
When launched by a share action (`ACTION_SEND` as the Intent action), it launches the main launch
Activity of Snapchat, called `com.snapchat.android.LandingPageActivity` with the same intent. So
basically, it functions as a wrapper around Snapchat to be a receiver of images.

The next step uses the Xposed framework to hook after the `onCreate()` method of the
`LandingPageActivity`. Now if the Intent is an `ACTION_SEND` Intent, Snapchat must have been
launched by Snapshare's image receiver Activity and the image URI is loaded into a Bitmap.

Before passing that Bitmap to Snapchat, the image is rotated if necessary, then cropped so that its
 aspect ratio is that of the display's viewing area and finally resized if it is larger that the
 viewing area.

Now an instance of `com.snapchat.android.util.eventbus.SnapCapturedEvent` is created with the just
 created Bitmap passed to the constructor. Finally, this instance is passed to the method
 `onSnapCaptured()` of the `LandingPageActivity`, which will load the `SnapPreviewFragment`
 displaying the image. Shazam!

License
-------
I like the GNU GPL, see the LICENSE file for further information.

*Copyright (C) 2013 Sebastian Stammler*
