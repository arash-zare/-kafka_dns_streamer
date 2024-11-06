package derak.cloud.stream.new_pipeline_kafka_dns_streamer.services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import derak.cloud.stream.new_pipeline_kafka_dns_streamer.avro.DnsLog;
import derak.cloud.stream.new_pipeline_kafka_dns_streamer.model.*;
//import derak.cloud.stream.new_pipeline_kafka_streamer.model.DerakServerData;
//import derak.cloud.stream.new_pipeline_kafka_streamer.model.PoolData;
//import derak.cloud.stream.new_pipeline_kafka_streamer.model.SubDomainPoolData;
//import derak.cloud.stream.new_pipeline_kafka_streamer.model.ZoneData;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.common.Strings;


@Slf4j
public class DerakServerInfoProcessor {
    public Map<String, ZoneData> zoneInfoMap;  // zone id -> zone info
    //    public Map<String, NodeInfo> nodeInfoMap; // node name -> node info
    private Map<String, String> domainInfoMap; // domain name -> zone_id

    public Map<String, String> nodeId2Name;
    private Map<String, DerakServerData> nodeInfo;

    private Map<String, PoolData> edgePoolInfoMap; // pool_id -> pool info

    private Map<String, PoolData> dnsPoolMemberInfoMap; // member id -> pool info

//    private Map<String, SubDomainPoolData> subDomainPoolsMap;


    private String getJsonString(JsonObject jsonObject, String fieldName) {
        JsonElement jsonElement = jsonObject.get(fieldName);
        if (jsonElement == null) {
            System.out.println(fieldName);
            return null;
        }
        return jsonElement.getAsString();
    }

    private Double getJsonDouble(JsonObject jsonObject, String fieldName) {
        JsonElement jsonElement = jsonObject.get(fieldName);
        if (jsonElement == null) {
            System.out.println(fieldName);
            return null;
        }
        return jsonElement.getAsDouble();
    }

    private boolean getJsonBoolean(JsonObject jsonObject, String fieldName) {
        JsonElement jsonElement = jsonObject.get(fieldName);
        if (jsonElement == null) {
            System.out.println(fieldName);
            return false;
        }
        return jsonElement.getAsBoolean();
    }


    public DerakServerInfoProcessor(String filePath) {
        try {
            readJsonFile(filePath);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }


    private ZoneData getZoneInfo(String zoneId, JsonObject jsonObject) {
        ZoneData zoneInfo = new ZoneData(getJsonString(jsonObject, "human_readable"),
                getJsonString(jsonObject, "edge_pool"),
                getJsonString(jsonObject, "user_segment"),
                getJsonString(jsonObject, "zone_status"),
                getJsonBoolean(jsonObject, "is_moved"),
                getJsonBoolean(jsonObject, "suspend"));

//        if (jsonObject.has("stub_edge_pool")) {
//            JsonArray stubEdgePool = jsonObject.get("stub_edge_pool").getAsJsonArray();
//            for (JsonElement jsonElement : stubEdgePool) {
//                JsonObject asJsonObject = jsonElement.getAsJsonObject();
//                for (Map.Entry<String, JsonElement> subDomain2Pool : asJsonObject.entrySet()) {
//                    String subDomain = subDomain2Pool.getKey();
//                    if (subDomain2Pool.getValue() == null) {
//                        System.out.println("subDomain2Pool.getValue is null");
//                    }
//                    String poolId = subDomain2Pool.getValue().getAsString();
//                    String poolName = null;
//                    if (edgePoolInfoMap.containsKey(poolId)) poolName = edgePoolInfoMap.get(poolId).getName();
//                    subDomainPoolsMap.put(subDomain, new SubDomainPoolData(poolId, poolName));
//                    break;
//                }
//            }
//        }
        return zoneInfo;
    }


    private void constructPoolInfo(JsonObject jsonObject) {
        edgePoolInfoMap = new HashMap<String, PoolData>();
        dnsPoolMemberInfoMap = new HashMap<String, PoolData>();
        for (Map.Entry<String, JsonElement> poolField : jsonObject.entrySet()) {
            String poolId = poolField.getKey();
            JsonObject poolInfoObject = poolField.getValue().getAsJsonObject();
            PoolData poolInfo = new PoolData();
            poolInfo.setId(poolId);
            poolInfo.setName(getJsonString(poolInfoObject, "pool_name"));
            String type = getJsonString(poolInfoObject, "pool_type");
            poolInfo.setType(type);
            if (type!=null && (type.equals("edge") || type.equals("custom-edge"))) {
                edgePoolInfoMap.put(poolId, poolInfo);
            } else { // if dns | custom-dns -> pool members are exactly in one pool
                JsonArray poolMembers = poolInfoObject.get("pool_members").getAsJsonArray();
                for (JsonElement rawPoolMember : poolMembers) {
                    if (rawPoolMember == null) {
//                        log.error("rawPoolMember is null");
                        continue;
                    }
                    String poolMemberId = rawPoolMember.getAsString();
                    if (nodeId2Name.containsKey(poolMemberId)) {
                        String poolMemberName = nodeId2Name.get(poolMemberId);
                        poolInfo.setNodeId(poolMemberId);
                        dnsPoolMemberInfoMap.put(poolMemberName, poolInfo);
                    }
                }
            }
        }
    }

    private void constructNodeInfo(JsonObject nodeInfoObject) {
        this.nodeId2Name = new HashMap<>();
        this.nodeInfo = new HashMap<>();
        for (Map.Entry<String, JsonElement> nodeEntity : nodeInfoObject.entrySet()) {
            String nodeId = nodeEntity.getKey();
            JsonObject nodeData = nodeEntity.getValue().getAsJsonObject();
            String ip = getJsonString(nodeData, "ip");
            String name = getJsonString(nodeData, "name");
            JsonObject geoData = nodeData.getAsJsonObject("geodata");
            nodeInfo.put(name, new DerakServerData(nodeId, ip,
                    getJsonString(geoData, "city"),
                    getJsonString(geoData, "continent"),
                    getJsonString(geoData, "country"),
                    getJsonString(geoData, "data_center"),
                    getJsonString(geoData, "host_name"),
                    getJsonDouble(geoData, "lat"),
                    getJsonDouble(geoData, "lon"),
                    getJsonString(geoData, "province"),
                    getJsonString(geoData, "rack"),
                    getJsonString(geoData, "region"),
                    getJsonString(geoData, "server")));
            nodeId2Name.put(nodeId, name);
        }
    }

    private void constructZoneInfo(JsonObject zoneRichObject) {
        zoneInfoMap = new HashMap<String, ZoneData>();
        domainInfoMap = new HashMap<String, String>();
//        subDomainPoolsMap = new HashMap<String, SubDomainPoolData>();
        for (Map.Entry<String, JsonElement> zoneField : zoneRichObject.entrySet()) {
            String zoneId = zoneField.getKey();
            JsonObject rawInfo = zoneField.getValue().getAsJsonObject();
            ZoneData zoneInfo = getZoneInfo(zoneId, rawInfo);
            PoolData poolInfo = edgePoolInfoMap.get(zoneInfo.getEdgePool());
            zoneInfo.setPoolName(poolInfo != null ? poolInfo.getName() : "");
            zoneInfoMap.put(zoneId, zoneInfo);
            domainInfoMap.put(zoneInfo.getHumanReadable(), zoneId);

        }
    }

    private void bootstrap(String jsonString) {

        JsonElement jsonTree = JsonParser.parseString(jsonString);
        JsonObject jsonObject = jsonTree.getAsJsonObject();
//
//        // create node info
        JsonObject nodeInfoObject = jsonObject.getAsJsonObject("node_info_mapping");
        constructNodeInfo(nodeInfoObject);
//
//        // construct pool info
        constructPoolInfo(jsonObject.getAsJsonObject("pool_info"));
//
//        // create domainInfo, zoneInfo
        JsonObject zoneRichObject = jsonObject.getAsJsonObject("zone_rich_data");
        constructZoneInfo(zoneRichObject);

    }

    public void readJsonFile(String filePath) throws IOException {
        Path path = Paths.get(filePath).toAbsolutePath();
        List<String> strings = Files.readAllLines(path);
        String jsonString = String.join(" ", strings);
        bootstrap(jsonString);

    }


    /**
     * ***************************************
     */

    public String findZoneName(String zoneId) {
        if (nodeId2Name.containsKey(zoneId)) {
            return nodeId2Name.get(zoneId);
        }
        return "";
    }

    public DerakServerData findNodeInfo(String nodeName) {
        if(nodeName==null)
            return null;
        if (!nodeInfo.containsKey(nodeName)) {
            return null;
        }
        return nodeInfo.get(nodeName);
    }

//    private ZoneData findZoneInfo(String zoneId, String host) {
//        if (zoneId == null)
//            return null;
//        if (!zoneInfoMap.containsKey(zoneId))
//            return null;
//        ZoneData zoneInfo = zoneInfoMap.get(zoneId);
//        if (subDomainPoolsMap.containsKey(host)) {
//            SubDomainPoolData subDomainPoolInfo = subDomainPoolsMap.get(host);
//            zoneInfo.setEdgePool(subDomainPoolInfo.getPoolId());
//            zoneInfo.setPoolName(subDomainPoolInfo.getPoolName());
//        }
//        return zoneInfo;
//    }

    private String findZoneId(String domain) {
        if (domainInfoMap.containsKey(domain))
            return domainInfoMap.get(domain);
        return null;
    }

    private void addNsInfo(String ns, DomainInfo domainInfo) {
        PoolData poolInfo = dnsPoolMemberInfoMap.get(ns.toLowerCase());//!
        if (poolInfo != null) {
            domainInfo.setPoolId(poolInfo.getId());
            domainInfo.setPoolName(poolInfo.getName());
            domainInfo.setPoolTye(poolInfo.getType());
        }
    }

    private DomainInfo findDomainInfo(String zoneId, String ns) {
        DomainInfo domainInfo = new DomainInfo(zoneId);
        if (zoneInfoMap.containsKey(zoneId)) {
            ZoneData zoneInfo = zoneInfoMap.get(zoneId);
            domainInfo.setUserSegment(zoneInfo.getUserSegment());
            domainInfo.setZoneStatus(zoneInfo.getZoneStatus());
        }
        addNsInfo(ns, domainInfo);
        return domainInfo;
    }

    private DomainInfo findZoneFromHost(String host, String ns) {
        String zoneId;
        String humanReadable;

        DomainInfo domainInfo;
        if (Strings.hasLength(host)) {
            int i = 0;
            zoneId = findZoneId(host);
            if (zoneId != null) {
                domainInfo = findDomainInfo(zoneId, ns);
                domainInfo.setHumanReadable(host);
                return domainInfo;
            }

            for (char c : host.toCharArray()) {
                if (c == '.') {
                    humanReadable = host.substring(i + 1);
                    zoneId = findZoneId(humanReadable);
                    if (zoneId != null) {
                        domainInfo = findDomainInfo(zoneId, ns);
                        domainInfo.setHumanReadable(humanReadable);
                        return domainInfo;
                    }
                }
                i++;
            }
        }
        // zone not found
        domainInfo = new DomainInfo(null);
//        addNsInfo(ns, domainInfo); //!
        return domainInfo;
    }

//    private NodeInfo findNodeInfo(String nodeName) {
//        if (!nodeInfoMap.containsKey(nodeName)) {
//            return null;
//        }
//        return nodeInfoMap.get(nodeName);
//    }


    public void enrichWithDerakInfo(DnsLog dnsInfo) {
        String host = dnsInfo.getHost();
        String ns = dnsInfo.getNs();

        DomainInfo domainInfo = findZoneFromHost(host, ns);//!
        String zoneId = domainInfo.getZoneId();

        dnsInfo.setZoneId(zoneId);
        dnsInfo.setDomains(domainInfo.getHumanReadable());
        dnsInfo.setPoolId(domainInfo.getPoolId());
        dnsInfo.setPoolName(domainInfo.getPoolName());
        dnsInfo.setPoolType(domainInfo.getPoolTye());
        dnsInfo.setUserSegment(domainInfo.getUserSegment());
        dnsInfo.setZoneStatus(domainInfo.getZoneStatus());

        if (!nodeInfo.containsKey(ns)) {
            return;
        }
        DerakServerData nodeInfo1 = nodeInfo.get(ns);
        if (nodeInfo1 == null) return;


        dnsInfo.setNsCity(nodeInfo1.getCity());
        dnsInfo.setNsContinent(nodeInfo1.getContinent());
        dnsInfo.setNsCountry(nodeInfo1.getCountry());
        dnsInfo.setNsLat(nodeInfo1.getLat());
        dnsInfo.setNsLon(nodeInfo1.getLon());
        dnsInfo.setNsDataCenter(nodeInfo1.getDataCenter());
        dnsInfo.setNsServerId(nodeInfo1.getServerID());
        dnsInfo.setNsServerIp(nodeInfo1.getVmServerIP());
        dnsInfo.setNsProvince(nodeInfo1.getProvince());
        dnsInfo.setNsRack(nodeInfo1.getRack());
        dnsInfo.setNsRegion(nodeInfo1.getRegion());
        dnsInfo.setNsPhysicalServerIp(nodeInfo1.getPhysicalServerIP());


//        dnsInfo.setInfoCity(nodeInfo1.getCity());
//        dnsInfo.setInfoContinent(nodeInfo1.getContinent());
//        dnsInfo.setInfoCountry(nodeInfo1.getCountry());
//        dnsInfo.setInfoLat(nodeInfo1.getLat());
//        dnsInfo.setInfoLon(nodeInfo1.getLon());
//        dnsInfo.setInfoDataCenter(nodeInfo1.getDataCenter());
//        dnsInfo.setInfoServerId(nodeInfo1.getServerID());
//        dnsInfo.setInfoServerIp(nodeInfo1.getVmServerIP());
//        dnsInfo.setInfoProvince(nodeInfo1.getProvince());
//        dnsInfo.setInfoRack(nodeInfo1.getRack());
//        dnsInfo.setInfoRegion(nodeInfo1.getRegion());
//        dnsInfo.setInfoPhysicalServerIp(nodeInfo1.getPhysicalServerIP());



    }

}
