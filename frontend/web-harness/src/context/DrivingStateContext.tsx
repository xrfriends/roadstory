import { createContext, type PropsWithChildren, useContext } from 'react';
import type { DrivingState } from '../platform/vehicle';

const DrivingStateContext = createContext<DrivingState>('UNKNOWN');
export function DrivingStateProvider({ state, children }: PropsWithChildren<{ state: DrivingState }>) { return <DrivingStateContext.Provider value={state}>{children}</DrivingStateContext.Provider>; }
// Provider와 Hook은 하나의 Context 모듈로 함께 유지한다.
// eslint-disable-next-line react-refresh/only-export-components
export function useDrivingState() { return useContext(DrivingStateContext); }
