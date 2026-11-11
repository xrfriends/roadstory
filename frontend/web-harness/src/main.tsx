import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { App } from './App';
import { ErrorBoundary } from './components/ErrorBoundary';
import { DrivingStateProvider } from './context/DrivingStateContext';
import type { DrivingState } from './platform/vehicle';
import './styles.css';

const state = (import.meta.env.VITE_FAKE_DRIVING_STATE ?? 'STOPPED') as DrivingState;
ReactDOM.createRoot(document.getElementById('root')!).render(<React.StrictMode><ErrorBoundary><DrivingStateProvider state={state}><BrowserRouter><App /></BrowserRouter></DrivingStateProvider></ErrorBoundary></React.StrictMode>);

