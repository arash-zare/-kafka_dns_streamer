package derak.cloud.stream.new_pipeline_kafka_dns_streamer.services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import derak.cloud.stream.new_pipeline_kafka_dns_streamer.model.GeoData;
import derak.cloud.stream.new_pipeline_kafka_dns_streamer.model.NetworkData;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;


public class NetworkProcessor {
    private List<NetworkData> privateNetworkInfo;

    public NetworkProcessor(String filePath) {
        try {
            privateNetworkInfo = new ArrayList<>();
            readJsonFile(filePath, privateNetworkInfo);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public GeoData findIpInfo(String ip, String serverName, String country) {
        IPAddress address = new IPAddressString(ip).getAddress();
        if(address == null)
            return null;
        for (NetworkData networkData : privateNetworkInfo) {
            String type = networkData.getType();
            boolean found = networkData.getBlock().contains(address);
            if (!found)
                continue;
            if (type.equals("any") ||
                    (type.equals("country") && networkData.getCountry().equals(country)) ||
                    (type.equals("name") && networkData.getServerName().equals(serverName))) {

                return networkData.getGeoData();
            }
        }
        return null;
    }


    private void readJsonFile(String filePath, List<NetworkData> privateNetworkInfo) throws IOException {
        privateNetworkInfo.clear();
        Path path = Paths.get(filePath).toAbsolutePath();
        List<String> strings = Files.readAllLines(path);
        String jsonString = String.join(" ", strings);

        JsonParser parser = new JsonParser();
        JsonElement jsonTree = parser.parse(jsonString);
        JsonObject jsonObject = jsonTree.getAsJsonObject();

        JsonArray networks = jsonObject.getAsJsonArray("networks");
        for (JsonElement networkElement : networks) {
            JsonObject network = networkElement.getAsJsonObject();
            String serverName = null;
            if (network.has("server_name"))
                serverName = network.get("server_name").getAsString();
            String serverCountry = null;
            if (network.has("server_country"))
                serverCountry = network.get("server_country").getAsString();
            String continentName = null;
            if (network.has("continent"))
                continentName = network.get("continent").getAsString();
            privateNetworkInfo.add(new NetworkData(
                    network.get("type").getAsString(),
                    serverCountry,
                    network.get("block").getAsString(),
                    network.get("asn").getAsInt(),
                    network.get("asn_org").getAsString(),
                    continentName,
                    network.get("country").getAsString(),
                    network.get("city").getAsString(),
                    serverName));
        }

    }
}
