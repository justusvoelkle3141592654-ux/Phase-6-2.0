import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'de.vokabeltrainer.app',
  appName: 'Vokabeltrainer',
  webDir: 'dist',
  server: {
    // The app runs on http://localhost, so it may call both a server in the
    // home network (http://192.168.x.y:3000) and one on the internet (https)
    // without mixed-content blocking.
    androidScheme: 'http',
  },
  android: {
    allowMixedContent: true,
  },
  plugins: {
    LocalNotifications: {
      smallIcon: 'ic_stat_vokabeltrainer',
    },
  },
};

export default config;
