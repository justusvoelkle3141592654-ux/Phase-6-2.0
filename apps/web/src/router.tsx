import { createBrowserRouter } from 'react-router';
import { AppShell } from './components/AppShell';
import { AuthGate } from './components/AuthGate';
import { HomePage } from './pages/HomePage';
import { LearnPage } from './pages/LearnPage';
import { PackageDetailPage } from './pages/PackageDetailPage';
import { PackagesPage } from './pages/PackagesPage';
import { DeclensionsPage } from './pages/DeclensionsPage';
import { SettingsPage } from './pages/SettingsPage';
import { UploadPage } from './pages/UploadPage';
import { UploadReviewPage } from './pages/UploadReviewPage';


export const routes = [
  {
    path: '/',
    element: (
      <AuthGate>
        <AppShell />
      </AuthGate>
    ),
    children: [
      { index: true, element: <HomePage /> },
      { path: 'upload', element: <UploadPage /> },
      { path: 'upload/:id', element: <UploadReviewPage /> },
      { path: 'packages', element: <PackagesPage /> },
      { path: 'packages/:id', element: <PackageDetailPage /> },
      { path: 'learn', element: <LearnPage /> },
      { path: 'declensions', element: <DeclensionsPage /> },
      { path: 'settings', element: <SettingsPage /> },
    ],
  },
];

export function createRouter() {
  return createBrowserRouter(routes);
}
