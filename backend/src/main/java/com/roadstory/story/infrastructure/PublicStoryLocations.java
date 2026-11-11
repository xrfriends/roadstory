package com.roadstory.story.infrastructure;

import com.roadstory.common.geo.GeoPoint;

public final class PublicStoryLocations {
    // 서울교통공사 역사 좌표 OA-22534, 2호선 성수역 211. 수제화 거리의 대표 지점.
    // https://data.seoul.go.kr/dataList/OA-22534/F/1/datasetView.do
    public static final GeoPoint SEONGSU_STATION = new GeoPoint(37.544628, 127.055983);

    private PublicStoryLocations() {}
}
