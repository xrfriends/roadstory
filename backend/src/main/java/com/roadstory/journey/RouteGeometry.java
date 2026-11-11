package com.roadstory.journey;

import com.roadstory.common.error.ApiException;
import com.roadstory.common.geo.GeoPoint;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;

import static com.roadstory.common.geo.GeoPoint.EARTH_RADIUS_METERS;

final class RouteGeometry {
    private final List<Segment> segments;

    private RouteGeometry(List<Segment> segments) {
        this.segments = List.copyOf(segments);
    }

    static RouteGeometry parse(String encoded, GeoPoint origin, GeoPoint destination) {
        if (origin == null || encoded.isEmpty() || encoded.length() > 25_000) {
            throw invalidShape();
        }

        var points = new ArrayList<GeoPoint>();
        var decoder = new PolylineDecoder(encoded);
        long latitude = 0;
        long longitude = 0;
        while (decoder.hasNext()) {
            latitude += decoder.nextDelta();
            longitude += decoder.nextDelta();
            double lat = latitude / 100_000d;
            double lon = longitude / 100_000d;
            if (!GeoPoint.isValid(lat, lon) || points.size() >= 5_000) {
                throw invalidShape();
            }
            points.add(new GeoPoint(lat, lon));
        }
        if (points.size() < 2 || origin.distanceTo(points.get(0)) > 300
            || destination.distanceTo(points.get(points.size() - 1)) > 300) {
            throw invalidShape();
        }

        var segments = new ArrayList<Segment>();
        double previousMeters = 0;
        for (int i = 1; i < points.size(); i++) {
            GeoPoint start = points.get(i - 1);
            GeoPoint end = points.get(i);
            double length = start.distanceTo(end);
            if (length > 0) {
                segments.add(new Segment(start, end, start.bearingTo(end), previousMeters, length));
                previousMeters += length;
            }
        }
        if (segments.isEmpty()) {
            throw invalidShape();
        }
        return new RouteGeometry(segments);
    }

    Projection closestTo(GeoPoint point) {
        Projection closest = null;
        for (Segment segment : segments) {
            Projection projection = segment.project(point);
            if (closest == null || projection.distanceMeters() < closest.distanceMeters()) {
                closest = projection;
            }
        }
        return closest;
    }

    record Projection(double progressMeters, double distanceMeters) {}

    private record Segment(GeoPoint start, GeoPoint end, double bearing, double progressMeters, double lengthMeters) {
        Projection project(GeoPoint point) {
            double distanceFromStart = start.distanceTo(point);
            double angularDistance = distanceFromStart / EARTH_RADIUS_METERS;
            double bearingDifference = Math.toRadians(start.bearingTo(point) - bearing);
            double alongMeters = EARTH_RADIUS_METERS * Math.atan2(
                Math.sin(angularDistance) * Math.cos(bearingDifference), Math.cos(angularDistance));
            if (alongMeters <= 0) {
                return new Projection(progressMeters, distanceFromStart);
            }
            if (alongMeters >= lengthMeters) {
                return new Projection(progressMeters + lengthMeters, end.distanceTo(point));
            }
            double acrossMeters = EARTH_RADIUS_METERS * Math.abs(
                Math.asin(Math.sin(angularDistance) * Math.sin(bearingDifference)));
            return new Projection(progressMeters + alongMeters, acrossMeters);
        }
    }

    private static ApiException invalidShape() {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_ROUTE_GEOMETRY",
            "확인할 수 없는 내비게이션 경로입니다.");
    }

    // Google Encoded Polyline Algorithm Format, precision 5.
    private static final class PolylineDecoder {
        private final String encoded;
        private int offset;

        PolylineDecoder(String encoded) {
            this.encoded = encoded;
        }

        boolean hasNext() {
            return offset < encoded.length();
        }

        long nextDelta() {
            long value = 0;
            for (int shift = 0; shift <= 25 && hasNext(); shift += 5) {
                int current = encoded.charAt(offset++) - 63;
                if (current < 0 || current > 63) {
                    throw invalidShape();
                }
                value |= (long) (current & 31) << shift;
                if (current < 32) {
                    return (value & 1) == 0 ? value >> 1 : ~(value >> 1);
                }
            }
            throw invalidShape();
        }
    }
}
