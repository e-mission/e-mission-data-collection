e-mission is a project to gather data about user travel patterns using phone apps, and use them to provide an personalized carbon footprint, and aggregate them to make data available to urban planners and transportation engineers.

The current version integrates with moves for the data collection. This repository tracks our attempts to move to our own data collection.

This currently contains only the android implementation of the data collection.

## Instructions ##
### Android ###
* This project is intended to be built with gradle, rather than ant.
* In case an IDE is used, the project is intended to be used with the IntelliJ-based Android Studio, not one that is based on eclipse.
* The LocationTests use mock locations. The related permission is in the debug AndroidManifest.xml, so the debug apk will require that permission, but the production apk will not.
* The LocationTests all PASS, but they take some time to complete. They appear to be somewhat flaky on custom emulators, but they do work reliably on an emulator based on the Nexus 5 with an xxhdpi density.
* In order to open the project, launch android studio and then select "Import Project". Navigate to the directory where you have cloned the project and select build.gradle. You should then be able to complete the project development workflow using Android Studio.

### iOS ###
* This project is intended to be built with XCode
* Open the `iOS/CFC_Tracker/CFC_Tracker.xcodeproj` to start working on the project
