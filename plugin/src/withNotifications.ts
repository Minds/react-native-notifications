import {
  withAppDelegate,
  withMainApplication,
  createRunOncePlugin,
} from "@expo/config-plugins";

import pkg from "react-native-notifications/package.json";

function withApplication(config) {
  return withMainApplication(config, (cfg) => {
    const { modResults } = cfg;
    const { contents } = modResults;
    const lines = contents.split("\n");

    const importIndex = lines.findIndex((line) =>
      /^import android.app.Application/.test(line)
    );

    const mainAppIndex = lines.findIndex((line) =>
      /^class MainApplication : Application\(\), ReactApplication {$/.test(line)
    );

    const onCreateIndex = lines.findIndex((line) =>
      /super.onCreate\(\)$/.test(line)
    );

    modResults.contents = [
      ...lines.slice(0, importIndex + 1),
      "import com.wix.reactnativenotifications.RNNotificationsPackage;",
      ...lines.slice(importIndex + 1, mainAppIndex + 1),
      "  var mRNNotificationsPackage: RNNotificationsPackage? = null",
      "      public get",
      "      private set",
      ...lines.slice(mainAppIndex + 1, onCreateIndex + 1),
      "    mRNNotificationsPackage = RNNotificationsPackage(this);",
      ...lines.slice(onCreateIndex + 1),
    ].join("\n");

    return cfg;
  });
}

function withNotifications(config) {
  config = withApplication(config);
  config = withAppDelegate(config, (cfg) => {
    const { modResults } = cfg;
    const { contents } = modResults;
    const lines = contents.split("\n");

    const importIndex = lines.findIndex((line) =>
      /^#import "AppDelegate.h"/.test(line)
    );
    const initialPropsIndex = lines.findIndex((line) =>
      /self\.initialProps = @{};/.test(line)
    );

    // change methods to call packatge instead of appplication
    const didRegisterForRemoteNotificationsIndex = lines.findIndex((line) =>
      /\s*return \[super application:application didRegisterForRemoteNotificationsWithDeviceToken:deviceToken\];/.test(
        line
      )
    );

    if (didRegisterForRemoteNotificationsIndex > -1) {
      lines[didRegisterForRemoteNotificationsIndex] =
        "  [RNNotifications didRegisterForRemoteNotificationsWithDeviceToken:deviceToken];";
    }

    const didFailToRegisterForRemoteNotificationsIndex = lines.findIndex(
      (line) =>
        /\s*return \[super application:application didFailToRegisterForRemoteNotificationsWithError:error\];/.test(
          line
        )
    );

    if (didFailToRegisterForRemoteNotificationsIndex > -1) {
      lines[didFailToRegisterForRemoteNotificationsIndex] =
        "  [RNNotifications didFailToRegisterForRemoteNotificationsWithError:error];";
    }

    const didReceiveRemoteNotificationIndex = lines.findIndex((line) =>
      /\s*return \[super application:application didReceiveRemoteNotification:userInfo fetchCompletionHandler:completionHandler\];/.test(
        line
      )
    );

    if (didReceiveRemoteNotificationIndex > -1) {
      lines[didReceiveRemoteNotificationIndex] =
        "  [RNNotifications didReceiveBackgroundNotification:userInfo withCompletionHandler:completionHandler];";
    }

    modResults.contents = [
      ...lines.slice(0, importIndex + 1),
      '#import "RNNotifications.h"',
      ...lines.slice(importIndex + 1, initialPropsIndex + 1),
      "  [RNNotifications startMonitorNotifications];",
      ...lines.slice(initialPropsIndex + 1),
    ].join("\n");

    return cfg;
  });

  return config;
}

export default createRunOncePlugin(withNotifications, pkg.name, pkg.version);
