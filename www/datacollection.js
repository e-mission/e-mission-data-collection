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
    markConsented: function (newConsent) {
        return new Promise(function(resolve, reject) {
            exec(resolve, reject, "DataCollection", "markConsented", [newConsent]);
        });
    },
    fixLocationSettings: function () {
        return new Promise(function(resolve, reject) {
            exec(resolve, reject, "DataCollection", "fixLocationSettings", []);
        });
    },
    isValidLocationSettings: function () {
        return new Promise(function(resolve, reject) {
            exec(resolve, reject, "DataCollection", "isValidLocationSettings", []);
        });
    },
    fixLocationPermissions: function () {
        return new Promise(function(resolve, reject) {
            exec(resolve, reject, "DataCollection", "fixLocationPermissions", []);
        });
    },
    isValidLocationPermissions: function () {
        return new Promise(function(resolve, reject) {
            exec(resolve, reject, "DataCollection", "isValidLocationPermissions", []);
        });
    },
    fixFitnessPermissions: function () {
        return new Promise(function(resolve, reject) {
            exec(resolve, reject, "DataCollection", "fixFitnessPermissions", []);
        });
    },
    isValidFitnessPermissions: function () {
        return new Promise(function(resolve, reject) {
            exec(resolve, reject, "DataCollection", "isValidFitnessPermissions", []);
        });
    },
    fixShowNotifications: function () {
        return new Promise(function(resolve, reject) {
            exec(resolve, reject, "DataCollection", "fixShowNotifications", []);
        });
    },
    isValidShowNotifications: function () {
        return new Promise(function(resolve, reject) {
            exec(resolve, reject, "DataCollection", "isValidShowNotifications", []);
        });
    },
    isNotificationsUnpaused: function () {
        return new Promise(function(resolve, reject) {
            exec(resolve, reject, "DataCollection", "isNotificationsUnpaused", []);
        });
    },
    fixUnusedAppRestrictions: function () {
        return new Promise(function(resolve, reject) {
            exec(resolve, reject, "DataCollection", "fixUnusedAppRestrictions", []);
        });
    },
    isUnusedAppUnrestricted: function () {
        return new Promise(function(resolve, reject) {
            exec(resolve, reject, "DataCollection", "isUnusedAppUnrestricted", []);
        });
    },
    fixIgnoreBatteryOptimizations: function () {
        return new Promise(function(resolve, reject) {
            exec(resolve, reject, "DataCollection", "fixIgnoreBatteryOptimizations", []);
        });
    },
    isIgnoreBatteryOptimizations: function () {
        return new Promise(function(resolve, reject) {
            exec(resolve, reject, "DataCollection", "isIgnoreBatteryOptimizations", []);
        });
    },
    fixOEMBackgroundRestrictions: function () {
        return new Promise(function(resolve, reject) {
            exec(resolve, reject, "DataCollection", "fixOEMBackgroundRestrictions", []);
        });
    },
    storeBatteryLevel: function () {
        return new Promise(function(resolve, reject) {
            exec(resolve, reject, "DataCollection", "storeBatteryLevel", []);
        });
    },
    // Switching both the get and set config to a promise to experiment with promises!!
    getConfig: function () {
        return new Promise(function(resolve, reject) {
            exec(resolve, reject, "DataCollection", "getConfig", []);
        });
    },
    setConfig: function (newConfig) {
        return new Promise(function(resolve, reject) {
            exec(resolve, reject, "DataCollection", "setConfig", [newConfig]);
        });
    },
    getAccuracyOptions: function () {
        return new Promise(function(resolve, reject) {
            exec(resolve, reject, "DataCollection", "getAccuracyOptions", []);
        });
    },
    getState: function () {
        return new Promise(function(resolve, reject) {
            exec(resolve, reject, "DataCollection", "getState", []);
        });
    },
    forceTransition: function (generalTransitionName) {
        return new Promise(function(resolve, reject) {
            exec(resolve, reject, "DataCollection", "forceTransition", [generalTransitionName]);
        });
    },
    handleSilentPush: function() {
        return new Promise(function(resolve, reject) {
            exec(resolve, reject, "DataCollection", "handleSilentPush",
                 []);
        })
    }
}

module.exports = DataCollection;
