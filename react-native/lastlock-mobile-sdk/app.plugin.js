const {
  withDangerousMod,
  withInfoPlist,
  withGradleProperties,
  withPodfile,
  withPodfileProperties,
  withProjectBuildGradle,
} = require(require.resolve('@expo/config-plugins', { paths: [process.cwd(), __dirname] }));
const fs = require('fs');
const path = require('path');

const BLUETOOTH_USAGE = 'This app uses Bluetooth to discover and connect to nearby locks.';

function withLastLockMobileSdk(config) {
  config = withDangerousMod(config, [
    'ios',
    (config) => {
      const projectName = config.modRequest.projectName;
      if (!projectName) {
        throw new Error('Last Lock Mobile plugin could not determine the iOS project name');
      }
      const dest = path.join(config.modRequest.platformProjectRoot, projectName);
      fs.mkdirSync(dest, { recursive: true });
      for (const file of ['LastLockMobileSdkModule.swift', 'LastLockMobileSdkModule.mm', 'LastLockMobileSdkController.h']) {
        fs.copyFileSync(path.join(__dirname, 'ios', file), path.join(dest, file));
      }
      fs.rmSync(path.join(dest, 'LastLockMobileSdkModule.m'), { force: true });
      return config;
    },
  ]);

  config = withPodfile(config, (config) => {
    const projectName = config.modRequest.projectName;
    if (!projectName) {
      throw new Error('Last Lock Mobile plugin could not determine the iOS project name');
    }
    let contents = config.modResults.contents;
    // Drop a hook that ran inside post_install. CocoaPods saves the user
    // project after that hook, which discards the Xcode changes.
    contents = contents.replace(
      /\n    load File\.expand_path\('[^']*integrate_lastlock_mobile\.rb', __dir__\)\n    integrate_lastlock_mobile\('[^']+'\)/g,
      ''
    );
    if (!contents.includes('integrate_lastlock_mobile')) {
      const scriptPath = path.join(__dirname, 'ios', 'integrate_lastlock_mobile.rb');
      const relative = path
        .relative(path.join(config.modRequest.projectRoot, 'ios'), scriptPath)
        .split(path.sep)
        .join('/');
      const snippet = [
        '  post_integrate do |installer|',
        '    load File.expand_path(' + `'${relative}', __dir__)`,
        `    integrate_lastlock_mobile('${projectName}')`,
        '  end',
      ].join('\n');
      const lines = contents.split('\n');
      let closing = -1;
      for (let i = lines.length - 1; i >= 0; i -= 1) {
        if (lines[i] === 'end') {
          closing = i;
          break;
        }
      }
      if (closing < 0) {
        throw new Error('Could not find the Podfile target block to attach Last Lock Mobile');
      }
      lines.splice(closing, 0, snippet);
      contents = lines.join('\n');
    }
    config.modResults.contents = contents;
    return config;
  });

  config = withPodfileProperties(config, (config) => {
    config.modResults['ios.deploymentTarget'] = '17.0';
    return config;
  });

  config = withInfoPlist(config, (config) => {
    config.modResults.NSBluetoothAlwaysUsageDescription =
      config.modResults.NSBluetoothAlwaysUsageDescription || BLUETOOTH_USAGE;
    config.modResults.NSBluetoothPeripheralUsageDescription =
      config.modResults.NSBluetoothPeripheralUsageDescription || BLUETOOTH_USAGE;
    return config;
  });

  config = withGradleProperties(config, (config) => {
    const key = 'android.minSdkVersion';
    const entry = config.modResults.find((item) => item.type === 'property' && item.key === key);
    if (entry) {
      entry.value = '26';
    } else {
      config.modResults.push({ type: 'property', key, value: '26' });
    }
    return config;
  });

  config = withProjectBuildGradle(config, (config) => {
    let contents = config.modResults.contents;
    if (!contents.includes('link-mobile-sdk-prod')) {
      const marker = "maven { url 'https://www.jitpack.io' }";
      if (!contents.includes(marker)) {
        throw new Error('Could not find the Android repository block to attach Last Lock Mobile');
      }
      contents = contents.replace(
        marker,
        `${marker}
        maven {
            url 'https://maven.pkg.github.com/last-lock/link-mobile-sdk-prod'
            credentials {
                username = rootProject.findProperty('gpr.user') ?: System.getenv('GITHUB_ACTOR')
                password = rootProject.findProperty('gpr.key') ?: System.getenv('GITHUB_TOKEN')
            }
        }`
      );
      config.modResults.contents = contents;
    }
    return config;
  });

  return config;
}

module.exports = withLastLockMobileSdk;
