package io.github.falydoor.mtakafka.producer.domain;

import org.apache.kafka.streams.kstream.Windowed;

import java.time.Instant;

public class SubwayCount {
    private String route;
    private long count;
    private Instant start;
    private Instant end;

    public SubwayCount(Windowed<String> key, long value) {
        this.route = key.key();
        this.count = value;
        this.start = Instant.ofEpochSecond(key.window().start() / 1000);
        this.end = Instant.ofEpochSecond(key.window().end() / 1000);
    }

    public String getRoute() {
        return route;
    }

    public void setRoute(String route) {
        this.route = route;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }

    public Instant getStart() {
        return start;
    }

    public void setStart(Instant start) {
        this.start = start;
    }

    public Instant getEnd() {
        return end;
    }

    public void setEnd(Instant end) {
        this.end = end;
    }

    @Override
    public String toString() {
        return "SubwayCount{" +
            "route='" + route + '\'' +
            ", count=" + count +
            ", start=" + start +
            ", end=" + end +
            '}';
    }
}
