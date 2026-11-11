package com.roadstory.journey;

public final class JourneyPorts {
    private JourneyPorts() {}

    public record GeoPoint(double latitude, double longitude) {}

    public interface LocationPort {
        GeoPoint currentLocation();
    }

    public interface DestinationPort {
        GeoPoint currentDestination();
    }

    public interface NavigationPort {
        void requestNavigation(GeoPoint destination);
        void onAudioInterrupt(boolean active);
    }

    public interface DestinationPlaylistComposer {
        Void compose();
    }

    public static final class FakeVehicleAdapter implements LocationPort, DestinationPort, NavigationPort {
        @Override
        public GeoPoint currentLocation() {
            return new GeoPoint(37.5665, 126.9780);
        }

        @Override
        public GeoPoint currentDestination() {
            return null;
        }

        @Override
        public void requestNavigation(GeoPoint destination) {}

        @Override
        public void onAudioInterrupt(boolean active) {}
    }
}
