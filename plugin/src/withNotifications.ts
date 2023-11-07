import {
  withAppDelegate,
  createRunOncePlugin,
} from "@expo/config-plugins";

import pkg from "react-native-notifications/package.json";

function withNotifications(config) {
  return withAppDelegate(config, (cfg) => {
    const { modResults } = cfg;
    const { contents } = modResults;
    const lines = contents.split("\n");

    const importIndex = lines.findIndex((line) =>
      /^#import "AppDelegate.h"/.test(line)
    );
    const initialPropsIndex = lines.findIndex((line) =>
      /self\.initialProps = @{};/.test(line)
    );
    const didLaunchIndex = lines.findIndex((line) =>
      /\[super application:application didFinishLaunchingWithOptions/.test(line)
    );

    modResults.contents = [
      ...lines.slice(0, importIndex + 1),
      '#import "RNNotifications.h"',
      ...lines.slice(importIndex + 1, initialPropsIndex + 1),
      "  [RNNotifications startMonitorNotifications];",
      ...lines.slice(initialPropsIndex + 1, didLaunchIndex + 2),
      "- (void)application:(UIApplication *)application didRegisterForRemoteNotificationsWithDeviceToken:(NSData *) deviceToken {",
      "  [RNNotifications didRegisterForRemoteNotificationsWithDeviceToken:deviceToken];",
      "}",
      "",
      "- (void)application:(UIApplication *)application didFailToRegisterForRemoteNotificationsWithError:(NSError *)error {",
      "  [RNNotifications didFailToRegisterForRemoteNotificationsWithError:error];",
      "}",
      ...lines.slice(didLaunchIndex + 2),
    ].join("\n");

    return cfg;
  });
}

export default createRunOncePlugin(withNotifications, pkg.name, pkg.version);
