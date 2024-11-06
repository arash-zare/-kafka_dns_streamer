package derak.cloud.stream.new_pipeline_kafka_dns_streamer.model;

public class SubDomainPoolData {
    private String poolId;
    private String poolName;

    public SubDomainPoolData(String poolId, String poolName){
        this.poolId = poolId;
        this.poolName = poolName;
    }

    public String getPoolId() {
        return poolId;
    }

    public void setPoolId(String poolId) {
        this.poolId = poolId;
    }

    public String getPoolName() {
        return poolName;
    }

    public void setPoolName(String poolName) {
        this.poolName = poolName;
    }
}
