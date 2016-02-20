e-mission is a project to gather data about user travel patterns using phone
apps, and use them to provide an personalized carbon footprint, and aggregate
them to make data available to urban planners and transportation engineers.

The current version integrates with moves for the data collection. This
repository tracks our attempts to move to our own data collection.

The data collection uses a geofence for duty cycling, the fused API for
location, and the built-in activity recognition for segmentation. 

## Instructions ##
This is currently a cordova plugin. This means that although the change is made
here, in the plugin, you need to test using the [e-mission-phone](https://github.com/e-mission/e-mission-phone) code, which
uses the plugin.

### Cross-platform development, principled ###

1. Check out this repo somewhere (say `~/e-mission/e-mission-data-collection`)
1. Follow the instructions to check out and run the [e-mission-phone](https://github.com/e-mission/e-mission-phone) code. That is a separate repo, so you will check it out in a separate directory (say `~/e-mission/e-mission-phone`)
1. Go into the phone directory `$cd ~/e-mission/e-mission-phone`
1. Remove the existing plugin (`$ cordova plugin remove edu.berkeley.eecs.emission.cordova.datacollection`)
1. Add your copy of the plugin (`$cordova plugin add ~/e-mission/e-mission-data-collection`)
1. Re-run the phone code
1. Every time you make a change to the plugin code, you need to remove and re-add it, and then you can test the change

### Quick development using the IDE ###

While the above way is principled, it is very slow. A faster approach is to use
the IDE for the appropriate platform.

1. Run the e-mission-phone code once so that it generates all the project files
1. Open the project in the IDE for the appropriate native code (see below)
1. make all the change to native code in the IDE and test them, and 
1. copy all the changes from `~/e-mission/e-mission/phone/platforms/android/src/edu/berkeley/eecs/emission/cordova/tracker` -> `~/e-mission/e-mission-data-collection/src/android`, and
1. finally check in the changes back into this repo from the `~/e-mission/e-mission-data-collection` directory.

#### Android ####
* This project is intended to be built with [Android Studio](https://developer.android.com/sdk/index.html) 
* Open `~/e-mission/e-mission-phone/platforms/android/build.gradle` to start working on the project

#### iOS ###
* This project is intended to be built with XCode
* Open the `~/e-mission/e-mission-phone/platforms/ios/emission.xcodeproj` to start working on the project
