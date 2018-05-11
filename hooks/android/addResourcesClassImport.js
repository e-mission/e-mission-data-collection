#!/usr/bin/env node

/*
 * A hook to add resources class (R.java) import to Android classes which uses it.
 */

function getRegexGroupMatches(string, regex, index) {
    index || (index = 1)

    var matches = [];
    var match;
    if (regex.global) {
        while (match = regex.exec(string)) {
            matches.push(match[index]);
            console.log('Match:', match);
        }
    }
    else {
        if (match = regex.exec(string)) {
            matches.push(match[index]);
        }
    }

    return matches;
}

// Adapted from 
// https://stackoverflow.com/a/5827895
// Using this and not one of the wrapper modules
// e.g. `file` or `node-dir` because I don't see an obvious way to add
// javascript modules using plugin.xml, and this uses only standard modules

var walk = function(ctx, dir, done) {
  var results = [];

  var fs = ctx.requireCordovaModule('fs'),
      path = ctx.requireCordovaModule('path');

  fs.readdir(dir, function(err, list) {
    if (err) return done(err);
    var pending = list.length;
    if (!pending) return done(null, results);
    list.forEach(function(file) {
      file = path.resolve(dir, file);
      fs.stat(file, function(err, stat) {
        if (stat && stat.isDirectory()) {
          walk(ctx, file, function(err, res) {
            results = results.concat(res);
            if (!--pending) done(null, results);
          });
        } else {
          results.push(file);
          if (!--pending) done(null, results);
        }
      });
    });
  });
};

module.exports = function (ctx) {
    // If Android platform is not installed, don't even execute
    if (ctx.opts.cordova.platforms.indexOf('android') < 0)
        return;

    var fs = ctx.requireCordovaModule('fs'),
        path = ctx.requireCordovaModule('path'),
        Q = ctx.requireCordovaModule('q');

    var deferral = Q.defer();

    var platformSourcesRoot = path.join(ctx.opts.projectRoot, 'platforms/android/src');
    var pluginSourcesRoot = path.join(ctx.opts.plugin.dir, 'src/android');

    var androidPluginsData = JSON.parse(fs.readFileSync(path.join(ctx.opts.projectRoot, 'plugins', 'android.json'), 'utf8'));
    var appPackage = androidPluginsData.installed_plugins[ctx.opts.plugin.id]['PACKAGE_NAME'];

    walk(ctx, pluginSourcesRoot, function (err, files) {
        console.log("walk callback with files = "+files);
        if (err) {
            console.error('Error when reading file:', err)
            deferral.reject();
            return
        }

        var deferrals = [];

        files.filter(function (file) { return path.extname(file) === '.java'; })
            .forEach(function (file) {
                var deferral = Q.defer();

                // console.log("Considering file "+file);
                var filename = path.basename(file);
                // console.log("basename "+filename);
                // var file = path.join(pluginSourcesRoot, filename);
                // console.log("newfile"+file);
                fs.readFile(file, 'utf-8', function (err, contents) {
                    if (err) {
                        console.error('Error when reading file:', err)
                        deferral.reject();
                        return
                    }

                    if (contents.match(/[^\.\w]R\./) || contents.match(/[^\.\w]BuildConfig\./) || contents.match(/[^\.\w]MainActivity\./)) {
                        console.log('Trying to get packages from file:', filename);
                        var packages = getRegexGroupMatches(contents, /package ([^;]+);/);
                        for (var p = 0; p < packages.length; p++) {
                            try {
                                var package = packages[p];
                                console.log('Handling package:', package);

                                var sourceFile = path.join(platformSourcesRoot, package.replace(/\./g, '/'), filename)
                                console.log('sourceFile:', sourceFile);
                                if (!fs.existsSync(sourceFile)) 
                                    throw 'Can\'t find file in installed platform directory: "' + sourceFile + '".';

                                var sourceFileContents = fs.readFileSync(sourceFile, 'utf8');
                                if (!sourceFileContents) 
                                    throw 'Can\'t read file contents.';

                                var newContents = sourceFileContents;

                                if (contents.match(/[^\.\w]R\./)) {
                                    newContents = sourceFileContents
                                        .replace(/(import ([^;]+).R;)/g, '')
                                        .replace(/(package ([^;]+);)/g, '$1 \n// Auto fixed by post-plugin hook \nimport ' + appPackage + '.R;');
                                }

                                // replace BuildConfig as well
                                if (contents.match(/[^\.\w]BuildConfig\./)) {
                                    newContents = newContents
                                        .replace(/(import ([^;]+).BuildConfig;)/g, '')
                                        .replace(/(package ([^;]+);)/g, '$1 \n// Auto fixed by post-plugin hook \nimport ' + appPackage + '.BuildConfig;');
                                }

                                // replace MainActivity as well
                                if (contents.match(/[^\.\w]MainActivity\./)) {
                                    newContents = newContents
                                        .replace(/(import ([^;]+).MainActivity;)/g, '')
                                        .replace(/(package ([^;]+);)/g, '$1 \n// Auto fixed by post-plugin hook \nimport ' + appPackage + '.MainActivity;');
                                }

                                fs.writeFileSync(sourceFile, newContents, 'utf8');
                                break;
                            }
                            catch (ex) {
                                console.log('Could not add import to "' +  filename + '" using package "' + package + '". ' + ex);
                            }
                        }
                    }
                });

                deferrals.push(deferral.promise);
            });

        Q.all(deferrals)
            .then(function() {
                console.log('Done with the hook!');
                deferral.resolve();
            })
    });

    return deferral.promise;
}
