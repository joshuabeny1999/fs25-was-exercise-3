package farm;

import cartago.Artifact;
import cartago.OPERATION;
import cartago.OpFeedbackParam;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class FarmKG extends Artifact {

    private static final String USERNAME = "joshua";
    private static final String PASSWORD = "joshua25";

    private String repoLocation;

    private static String PREFIXES = "PREFIX was: <https://was-course.interactions.ics.unisg.ch/farm-ontology#>\n" +
            "PREFIX hmas: <https://purl.org/hmas/>\n" +
            "PREFIX td: <https://www.w3.org/2019/wot/td#>\n";

    public void init(String repoLocation) {
        this.repoLocation = repoLocation;
    }

    @OPERATION
    public void queryFarm(OpFeedbackParam<String> farm) {
        String farmValue = null;
        String farmVariableName = "farm";
        // Query all farms (assumes data is in the default graph)
        String queryStr = PREFIXES + "SELECT ?" + farmVariableName + " WHERE { ?" + farmVariableName + " a was:Farm. }";
        JsonArray farmBindings = executeQuery(queryStr);
        if (farmBindings.size() > 0) {
            JsonObject firstBinding = farmBindings.get(0).getAsJsonObject();
            JsonObject farmBinding = firstBinding.getAsJsonObject(farmVariableName);
            farmValue = farmBinding.getAsJsonPrimitive("value").getAsString();
        } else {
            farmValue = "No farm found";
        }
        farm.set(farmValue);
    }

    @OPERATION
    public void queryThing(String farm, String offeredAffordance, OpFeedbackParam<String> thingDescription) {
        String tdValue = null;
        String tdVariableName = "td";
        // Query to get the thing description (profile) for a tractor offering the specified affordance
        String queryStr = PREFIXES + "SELECT ?" + tdVariableName + " WHERE {\n" +
                "<" + farm + "> hmas:contains ?thing.\n" +
                "?thing td:hasActionAffordance ?aff.\n" +
                "?thing hmas:hasProfile ?" + tdVariableName + ".\n" +
                "?aff a <" + offeredAffordance + ">.\n" +
                "} LIMIT 1";
        JsonArray tdBindings = executeQuery(queryStr);
        if (tdBindings.size() > 0) {
            JsonObject firstBinding = tdBindings.get(0).getAsJsonObject();
            JsonObject tdBinding = firstBinding.getAsJsonObject(tdVariableName);
            tdValue = tdBinding.getAsJsonPrimitive("value").getAsString();
        } else {
            tdValue = "No TD found";
        }
        thingDescription.set(tdValue);
    }

    @OPERATION
    public void queryFarmSections(String farm, OpFeedbackParam<Object[]> sections) {
        // Query to retrieve all land sections of the given farm
        String queryStr = PREFIXES + "SELECT ?section WHERE { <" + farm + "> was:hasLandSection ?section. }";
        JsonArray bindings = executeQuery(queryStr);
        Object[] result = new Object[bindings.size()];
        for (int i = 0; i < bindings.size(); i++) {
            JsonObject binding = bindings.get(i).getAsJsonObject();
            String section = binding.getAsJsonObject("section").getAsJsonPrimitive("value").getAsString();
            result[i] = section;
        }
        sections.set(result);
    }

    @OPERATION
    public void querySectionCoordinates(String section, OpFeedbackParam<Object[]> coordinates) {
        // Query to retrieve the coordinates for the given land section
        String queryStr = PREFIXES + "SELECT ?x1 ?y1 ?x2 ?y2 WHERE { <" + section + "> was:hasCoordinates ?coords. " +
                "?coords was:hasCoordinateX1 ?x1; " +
                "was:hasCoordinateY1 ?y1; " +
                "was:hasCoordinateX2 ?x2; " +
                "was:hasCoordinateY2 ?y2. }";
        JsonArray bindings = executeQuery(queryStr);
        Object[] result;
        if (bindings.size() > 0) {
            JsonObject binding = bindings.get(0).getAsJsonObject();
            double x1 = binding.getAsJsonObject("x1").getAsJsonPrimitive("value").getAsInt();
            double y1 = binding.getAsJsonObject("y1").getAsJsonPrimitive("value").getAsInt();
            double x2 = binding.getAsJsonObject("x2").getAsJsonPrimitive("value").getAsInt();
            double y2 = binding.getAsJsonObject("y2").getAsJsonPrimitive("value").getAsInt();
            result = new Object[]{x1, y1, x2, y2};
        } else {
            result = new Object[]{"No coordinates found"};
        }
        coordinates.set(result);
    }

    @OPERATION
    public void queryCropOfSection(String section, OpFeedbackParam<String> crop) {
        // Query to get the crop growing in the specified land section
        String queryStr = PREFIXES + "SELECT ?crop WHERE { <" + section + "> was:hasCrop ?crop. } LIMIT 1";
        JsonArray bindings = executeQuery(queryStr);
        String cropValue;
        if (bindings.size() > 0) {
            JsonObject binding = bindings.get(0).getAsJsonObject();
            cropValue = binding.getAsJsonObject("crop").getAsJsonPrimitive("value").getAsString();
        } else {
            cropValue = "No crop found";
        }
        crop.set(cropValue);
    }

    @OPERATION
    public void queryRequiredMoisture(String crop, OpFeedbackParam<Integer> level) {
        // Query to get the required moisture level for the given crop
        String queryStr = PREFIXES + "SELECT ?level WHERE { <" + crop + "> was:requiredMoistureLevel ?level. } LIMIT 1";
        JsonArray bindings = executeQuery(queryStr);
        Integer moistureLevelValue;
        if (bindings.size() > 0) {
            JsonObject binding = bindings.get(0).getAsJsonObject();
            moistureLevelValue = binding.getAsJsonObject("level").getAsJsonPrimitive("value").getAsInt();
        } else {
            moistureLevelValue = -1;
        }
        level.set(moistureLevelValue);
    }

    private JsonArray executeQuery(String queryStr) {
        String queryUrl = this.repoLocation + "?query=" + URLEncoder.encode(queryStr, StandardCharsets.UTF_8);
        try {
            URI uri = new URI(queryUrl);

            // Enable basic authentication
            String authString = USERNAME + ":" + PASSWORD;
            byte[] authBytes = authString.getBytes(StandardCharsets.UTF_8);
            String encodedAuth = Base64.getEncoder().encodeToString(authBytes);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("Authorization", "Basic " + encodedAuth)
                    .header("Accept", "application/sparql-results+json")
                    .GET()
                    .build();

            HttpClient client = HttpClient.newHttpClient();
            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    throw new RuntimeException("HTTP error code : " + response.statusCode());
                }
                String responseBody = response.body();
                JsonObject jsonObject = new Gson().fromJson(responseBody, JsonObject.class);
                JsonArray bindingsArray = jsonObject.getAsJsonObject("results").getAsJsonArray("bindings");
                return bindingsArray;
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return new JsonArray();
    }
}