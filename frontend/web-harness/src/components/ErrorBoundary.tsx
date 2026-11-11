import { Component, type ErrorInfo, type ReactNode } from 'react';
import { ErrorState } from './States';

export class ErrorBoundary extends Component<{ children: ReactNode }, { failed: boolean }> {
  state = { failed: false };
  static getDerivedStateFromError() { return { failed: true }; }
  componentDidCatch(error: Error, info: ErrorInfo) {
    // 원문을 Log에 기록하지 않되 Error Boundary lifecycle은 유지한다.
    void error;
    void info;
  }
  render() { return this.state.failed ? <ErrorState /> : this.props.children; }
}
