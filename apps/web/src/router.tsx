import { createBrowserRouter } from 'react-router';
import { AppShell } from './components/AppShell';
import { HomePage } from './pages/HomePage';
import { LearnPage } from './pages/LearnPage';
import { PackagesPage } from './pages/PackagesPage';
import { SettingsPage } from './pages/SettingsPage';
import { UploadPage } from './pages/UploadPage';

export const routes = [
  {
    path: '/',
    element: <AppShell />,
    children: [
      { index: true, element: <HomePage /> },
      { path: 'upload', element: <UploadPage /> },
      { path: 'packages', element: <PackagesPage /> },
      { path: 'learn', element: <LearnPage /> },
      { path: 'settings', element: <SettingsPage /> },
    ],
  },
];

export function createRouter() {
  return createBrowserRouter(routes);
}
