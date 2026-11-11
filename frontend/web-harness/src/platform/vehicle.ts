export type DrivingState = 'DRIVING' | 'STOPPED' | 'UNKNOWN';
export type Coordinates = { latitude: number; longitude: number };

export interface VehiclePlatform {
  getDrivingState(): Promise<DrivingState>;
  getCurrentLocation(): Promise<Coordinates | null>;
  getDestination(): Promise<Coordinates | null>;
  onNavigationAudioInterrupt(listener: (active: boolean) => void): () => void;
  requestNavigation(destination: Coordinates): Promise<void>;
}

export class FakeVehiclePlatform implements VehiclePlatform {
  constructor(private state: DrivingState = 'STOPPED') {}
  async getDrivingState() { return this.state; }
  async getCurrentLocation() { return { latitude: 37.544, longitude: 127.056 }; }
  async getDestination() { return null; }
  onNavigationAudioInterrupt() { return () => undefined; }
  async requestNavigation() { return undefined; }
}

