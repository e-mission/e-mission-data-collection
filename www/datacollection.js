/*global cordova, module*/

var exec = require("cordova/exec")

/*
 * Format of the returned value:
 * {
 *    "isDutyCycling": true/false,
 *    "accuracy": "high/balanced/hundredmeters/..",
 *    "geofenceRadius": 1234,
 *    "accuracyThreshold": 1234,
 *    "filter": "time/distance",
 *    "filterValue": 1234,
 *    "tripEndStationaryMins": 1234
 * }
 */

var DataCollection = {
    /*
     * Launch init: registers all the callbacks necessary on launch, mainly for
     * iOS, where we have to re-register the listeners for fine location
     * tracking every time the app is launched.
     */
    launchInit: function (successCallback, errorCallback) {
        exec(successCallback, errorCallback, "DataCollection", "launchInit", []);
    },
    getConfig: function (successCallback, errorCallback) {
        exec(successCallback, errorCallback, "DataCollection", "getConfig", []);
    },
    getState: function (successCallback, errorCallback) {
        exec(successCallback, errorCallback, "DataCollection", "getState", []);
    },
    forceTripStart: function (successCallback, errorCallback) {
        exec(successCallback, errorCallback, "DataCollection", "forceTripStart", []);
    },
    forceTripEnd: function (successCallback, errorCallback) {
        exec(successCallback, errorCallback, "DataCollection", "forceTripEnd", []);
    },
    forceRemotePush: function (successCallback, errorCallback) {
        exec(successCallback, errorCallback, "DataCollection", "forceRemotePush", []);
    }
}

module.exports = DataCollection;
