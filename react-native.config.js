module.exports = {
  dependency: {
    platforms: {
      ios: {},
      android: {
        sourceDir: './android/app',
        packageInstance: 'new RNNotificationsPackage(reactNativeHost.getApplication())',
      }
    },
  },
  project: {
    ios: {
      project: './ios/NotificationsExampleApp.xcworkspace',
    },
    android: {
      sourceDir: './example/android/',
    },
  },
};
