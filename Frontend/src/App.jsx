/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';
import { BrowserRouter } from 'react-router-dom';
import AppRoutes from './routes/AppRoutes';
import { ToastProvider } from './components/ui/Toast';

export default function App() {
  return (
    <ToastProvider>
      <BrowserRouter>
        <AppRoutes />
      </BrowserRouter>
    </ToastProvider>
  );
}
