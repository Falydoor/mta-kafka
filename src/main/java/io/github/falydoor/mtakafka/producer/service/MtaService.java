package io.github.falydoor.mtakafka.producer.service;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.transit.realtime.GtfsRealtime;
import io.github.falydoor.mtakafka.producer.config.MessagingConfiguration;
import io.github.falydoor.mtakafka.producer.domain.Subway;
import io.github.falydoor.mtakafka.producer.domain.SubwayCount;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.kstream.Windowed;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.http.ResponseEntity;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Service
public class MtaService {
    private final Logger log = LoggerFactory.getLogger(MtaService.class);

    private static final String MTA_KEY = "";

    private final MessagingConfiguration.MtaStream mtaStream;

    private final RestTemplate restTemplate;

    private final InfluxDB influxDB;

    public MtaService(MessagingConfiguration.MtaStream mtaStream) {
        this.mtaStream = mtaStream;
        this.restTemplate = new RestTemplate();

        // Init influxDB
        this.influxDB = InfluxDBFactory.connect("http://127.0.0.1:8086", "root", "root");
        this.influxDB.setDatabase("mta");
    }

    @Scheduled(cron = "0 */5 * * * *")
    public void publishMtaFeeds() {
        // Feed ids, more details at https://datamine.mta.info/list-of-feeds
        IntStream feedIds = IntStream.of(1, 2, 11, 16, 21, 26, 31, 36, 51);

        // Read each feed and build a list of active subways
        List<Subway> subways = feedIds
            .mapToObj(Integer::toString)
            .flatMap(this::readMtaFeed)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        // Publish all subways
        mtaStream.output().send(MessageBuilder.withPayload(subways).build());
    }

    @StreamListener("input")
    @SendTo("output")
    public KStream<?, SubwayCount> streamMtaFeeds(KStream<Object, List<Map<String, String>>> input) {
        // Count subways for each route with a window of 5 mins
        // Then publish the stream
        return input
            .flatMapValues(value -> value.stream().map(subway -> subway.get("route")).collect(Collectors.toList()))
            .map((key, value) -> new KeyValue<>(value, value))
            .groupByKey()
            .windowedBy(TimeWindows.of(5 * 60 * 1000))
            .count(Materialized.as("subwaycounts"))
            .toStream()
            .map(this::createSubwayCount);
    }

    @StreamListener(MessagingConfiguration.MtaStream.INPUT)
    public void saveSubwayCount(SubwayCount subwayCount) {
        // Save measurement in influxdb
        influxDB.write(Point.measurement(subwayCount.getRoute())
            .time(subwayCount.getStart().toEpochMilli(), TimeUnit.MILLISECONDS)
            .tag("route", subwayCount.getRoute())
            .addField("count", subwayCount.getCount())
            .build());
    }

    private Stream<Subway> readMtaFeed(String id) {
        log.info("Reading feed for id {}", id);
        try {
            // Call MTA api
            ResponseEntity<byte[]> response = restTemplate.getForEntity("http://datamine.mta.info/mta_esi.php?key={0}&feed_id={1}", byte[].class, MTA_KEY, id);

            // Parse response using protobuff
            // All subways with no active trip are removed
            return GtfsRealtime.FeedMessage.parseFrom(response.getBody()).getEntityList().stream()
                .filter(GtfsRealtime.FeedEntity::hasTripUpdate)
                .map(this::createSubway);
        } catch (InvalidProtocolBufferException e) {
            log.error("Error while parsing MTA feed", e);
            return null;
        }
    }

    private Subway createSubway(GtfsRealtime.FeedEntity entity) {
        Subway subway = new Subway();
        subway.setRoute(entity.getTripUpdate().getTrip().getRouteId());
        subway.setTrip(entity.getTripUpdate().getTrip().getTripId());
        return subway;
    }

    private KeyValue<?, SubwayCount> createSubwayCount(Windowed<String> key, long value) {
        SubwayCount routeCount = new SubwayCount();
        routeCount.setRoute(key.key());
        routeCount.setCount(value);
        routeCount.setStart(Instant.ofEpochSecond(key.window().start() / 1000));
        routeCount.setEnd(Instant.ofEpochSecond(key.window().end() / 1000));
        return new KeyValue<>(null, routeCount);
    }
}
