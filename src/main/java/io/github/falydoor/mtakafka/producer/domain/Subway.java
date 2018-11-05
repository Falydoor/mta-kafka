package io.github.falydoor.mtakafka.producer.domain;

import com.google.transit.realtime.GtfsRealtime;

public class Subway {
    private String trip;
    private String route;

    public Subway(GtfsRealtime.FeedEntity feedEntity) {
        this.route = feedEntity.getTripUpdate().getTrip().getRouteId();
        this.trip = feedEntity.getTripUpdate().getTrip().getTripId();
    }

    public String getRoute() {
        return route;
    }

    public void setRoute(String route) {
        this.route = route;
    }

    public String getTrip() {
        return trip;
    }

    public void setTrip(String trip) {
        this.trip = trip;
    }
}
