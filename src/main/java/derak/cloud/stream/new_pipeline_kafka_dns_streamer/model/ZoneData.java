package derak.cloud.stream.new_pipeline_kafka_dns_streamer.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ZoneData {
    private String edgePool;
    private String humanReadable;
    private boolean isMoved; // "False"
    private boolean suspend;
    private String userSegment;
    private String zoneStatus;
    private String poolName;


    public ZoneData(String humanReadable, String edgePool, String userSegment,
                    String zoneStatus, boolean isMoved, boolean suspend) {
        this.humanReadable = humanReadable;
        this.edgePool = edgePool;
        this.userSegment = userSegment;
        this.zoneStatus = zoneStatus;
        this.isMoved = isMoved;
        this.suspend = suspend;
    }
    @Override
    public String toString() {
        return String.format("{edge_pool:%s, human_readable:%s, user_segment:%s, zone_status:%s, pool_name:%s}",
                edgePool, humanReadable, userSegment, zoneStatus, poolName);
    }
}
