"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const config_plugins_1 = require("@expo/config-plugins");
const package_json_1 = __importDefault(require("react-native-notifications/package.json"));
function withNotifications(config) {
    return config_plugins_1.withAppDelegate(config, (cfg) => {
        const { modResults } = cfg;
        const { contents } = modResults;
        const lines = contents.split("\n");
        const importIndex = lines.findIndex((line) => /^#import "AppDelegate.h"/.test(line));
        const initialPropsIndex = lines.findIndex((line) => /self\.initialProps = @{};/.test(line));
        const didLaunchIndex = lines.findIndex((line) => /\[super application:application didFinishLaunchingWithOptions/.test(line));
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
exports.default = config_plugins_1.createRunOncePlugin(withNotifications, package_json_1.default.name, package_json_1.default.version);
