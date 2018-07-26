# File Cloud upload plug-in

# 1. Overview
Cloud plug-in uploads still images directly from RICOH THETA V to Google Photos server.
Uploaded still images can be seen in the "Drop box" album of [Google Photos](https://photos.google.com/).

# 2. Terms of Service

> You agree to comply with all applicable export and import laws and regulations applicable to the jurisdiction in which the Software was obtained and in which it is used. Without limiting the foregoing, in connection with use of the Software, you shall not export or re-export the Software  into any U.S. embargoed countries (currently including, but necessarily limited to, Crimea – Region of Ukraine, Cuba, Iran, North Korea, Sudan and Syria) or  to anyone on the U.S. Treasury Department’s list of Specially Designated Nationals or the U.S. Department of Commerce Denied Person’s List or Entity List.  By using the Software, you represent and warrant that you are not located in any such country or on any such list.  You also agree that you will not use the Software for any purposes prohibited by any applicable laws, including, without limitation, the development, design, manufacture or production of missiles, nuclear, chemical or biological weapons.

By using the Wireless Live Streaming plug-in, you are agreeing to the above and the license terms, [LICENSE.txt](LICENSE.txt).

Copyright &copy; 2018 Ricoh Company, Ltd.

# 3. Build and Environment

## 3-1. Hardware

* RICOH THETA V
* Firmware ver.2.31.1 and above

> Information on checking and updating the firmware is [here](https://theta360.com/en/support/manual/v/content/pc/pc_09.html).

## 3-2.  Development Environment

This plug-in has been built under the following conditions.

#### Operating System

* Windows&trade; 10 Version 1709
* macOS&reg; High Sierra ver.10.13

#### Development environment

* Android&trade; Studio 3.1+
* gradle 3.1.3
* Android SDK (API Level 25)
* compileSdkVersion 26
* buildToolsVersion "27.0.3"
* minSdkVersion 25
* targetSdkVersion 25

## 3-3.  Prepare to build

The source code here is not exactly same as the one on [PLUG-IN store](https://pluginstore.theta360.com/plugins/com.theta360.cloudupload/), because Google client id and secret key is for private.
At least, **it is needed to set your client id and secret in ["api.properties"](https://github.com/ricohapi/theta-cloud-upload-plugin/blob/master/app/src/main/assets/api.properties)** to work fine after build.
See ["Setting up OAuth 2.0"](https://support.google.com/cloud/answer/6158849) in detail.

# 4. Install
Android Studio install apk after build automatically. Or use the following command after build.

```
adb install -r app-debug.apk
```

Or install from [PLUG-IN store](https://pluginstore.theta360.com/plugins/com.theta360.cloudupload/) by using RICOH THETA app for Windows or macOS.

### Give permissions for this plug-in.

Using desktop viewing app as Vysor, open Settings app and turns on the permissions at "Apps" > "Wireless Live Streaming" > "Permissions"

# 5. How to Use
The plugin setting (login to Google) is needed only once at the first time to upload.
After the setting, photos in THETA V can be uploaded by pressing shutter button after launching this plug-in.


1. Turn on the THETA.
2. Open RICOH THETA app on your Win/Mac.
3. Set this plug-in as an active plugin from "File" > "Plug-in management..."
4. Connect THETA V to Wireless-LAN by client mode  
   For example, let's assume that there are a THETA V, a macOS machine and an iPhone on the same wireless LAN.  
5. Set active plug-in  
   Open the THETA mobile app on an iOS / Android smartphone
   Tap "Settings" at right bottom corner  
   Confirm "Connection" is "Wi-Fi" or "Wi-Fi+Bluetooth".  
   Tap "Camera settings"  
   Tap "Plug-in"  
   Select "File cloud upload"  
6. Check IP address of the camera
   Back to the Camera settings  
   Check IP-address of THETA V on smartphone app
   If you use macOS type "dns-sd -q THETAYL01234567.local" in Terminal. Here "THETAYL01234567" is an example, please change to your serial number.
7. Launch plug-in
   There are three ways to launch "File cloud upload".
   a. Press Mode button till LED2 turns white
   b. Open the URL "http://(ip-address)/plugin" on the browser
   c. Launch plug-in from the smartphone app (RICOH THETA)
8. Open WebUI of plug-in  
    Open the URL "http://(ip-address):8888/" on the browser   
    Here, (ip-address) is example. Change it to your THETA V's IP address.  
9. Authenticate your Google Account
    Press "Unregistered" at the right of "Google Photos"
    Copy the code displayed in the web page and press "Login".
    New window will be opened. It is google's site. Please following the message of the page. The code which is copied above will be needed to enter.
    After login to Google, back to the original web page.
    Press "Done" at the right of the title "Google Photos".
10. Start upload
    Press shutter button of the camera
     or
    Press "Start uploading" button on the WebUI
11. (option) Stop upload
    During upload, Camera and LIVE LEDs are blinking. Progress is shown on the WebUI.
    To stop upload,
    Press shutter button
     or
    Press "Stop uploading" button on the WebUI
12. Finish plug-in
    Press Mode button of the camera till LED2 turns blue
     or
    Press power button of the camera to sleep

# 6. History
* ver.1.0.9 (2018/07/23): Initial version.

---

## Trademark Information

The names of products and services described in this document are trademarks or registered trademarks of each company.

* Android, Nexus, Google Chrome, Google Play, Google Play logo, Google Maps, Google+, Gmail, Google Drive, Google Cloud Print and YouTube are trademarks of Google Inc.
* Apple, Apple logo, Macintosh, Mac, Mac OS, OS X, AppleTalk, Apple TV, App Store, AirPrint, Bonjour, iPhone, iPad, iPad mini, iPad Air, iPod, iPod mini, iPod classic, iPod touch, iWork, Safari, the App Store logo, the AirPrint logo, Retina and iPad Pro are trademarks of Apple Inc., registered in the United States and other countries. The App Store is a service mark of Apple Inc.
* Microsoft, Windows, Windows Vista, Windows Live, Windows Media, Windows Server System, Windows Server, Excel, PowerPoint, Photosynth, SQL Server, Internet Explorer, Azure, Active Directory, OneDrive, Outlook, Wingdings, Hyper-V, Visual Basic, Visual C ++, Surface, SharePoint Server, Microsoft Edge, Active Directory, BitLocker, .NET Framework and Skype are registered trademarks or trademarks of Microsoft Corporation in the United States and other countries. The name of Skype, the trademarks and logos associated with it, and the "S" logo are trademarks of Skype or its affiliates.
* Wi-Fi™, Wi-Fi Certified Miracast, Wi-Fi Certified logo, Wi-Fi Direct, Wi-Fi Protected Setup, WPA, WPA 2 and Miracast are trademarks of the Wi-Fi Alliance.
* The official name of Windows is Microsoft Windows Operating System.
* All other trademarks belong to their respective owners.
