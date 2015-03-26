package se.kodapan.osm.orebro.v2;

import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import se.kodapan.geojson.Feature;
import se.kodapan.geojson.FeatureCollection;
import se.kodapan.geojson.GeoJSONParser;
import se.kodapan.geojson.Point;
import se.kodapan.osm.domain.Node;
import se.kodapan.osm.domain.root.PojoRoot;
import se.kodapan.osm.xml.OsmXmlWriter;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author kalle
 * @since 2015-03-21 13:03
 */
public class Orebro {

  public static void main(String[] args) throws Exception {
    new Orebro().run();
  }


  private Map<Integer, JSONObject> layersTags = new HashMap<>();

  public void run() throws Exception {
    JSONArray layers = getLayers();

    JSONArray layersTags = new JSONArray(new JSONTokener(new InputStreamReader(new FileInputStream("src/main/java/se/kodapan/osm/orebro/v2/layer-tags.json"), "UTF8")));
    for (int i=0; i<layersTags.length(); i++) {
      JSONObject layerTags = layersTags.getJSONObject(i);
      this.layersTags.put(layerTags.getInt("id"), layerTags);
    }

    for (int i = 0; i < layers.length(); i++) {
      JSONObject layer = layers.getJSONObject(i);
      if ("vectorLayer".equals(layer.getString("type"))) {
        int id = layers.getJSONObject(i).getInt("id");
        FeatureCollection featureCollection = getLayer(id);

        if (!this.layersTags.containsKey(id)) {
          throw new RuntimeException("No tags defined for layer " + id + "\n" + layer.toString());
        }

        PojoRoot root = new PojoRoot();

        long osmId = -1;

        for (Feature feature : featureCollection.getFeatures()) {

          Node node = new Node();
          node.setId(osmId--);

          Point point = (Point) feature.getGeometry();
          node.setLatitude(point.getLatitude());
          node.setLongitude(point.getLongitude());

          JSONObject tags = this.layersTags.get(id).getJSONObject("tags");
          for (Iterator tagKeys = tags.keys(); tagKeys.hasNext();) {
            String tagKey = (String)tagKeys.next();
            String tagValue = tags.getString(tagKey);
            node.setTag(tagKey, tagValue);
          }

          String description = null;
          String title = null;

          if (feature.getProperties().has("description")) {
            description = feature.getProperties().getString("description").trim();
            if (description.isEmpty()) {
              description = null;
            } else {
              description = description.replaceAll("\\s+", " ");
            }

          }

          if (feature.getProperties().has("title")) {
            title = feature.getProperties().getString("title").trim();
            if (title.isEmpty()) {
              title = null;
            } else {
              title = title.replaceAll("\\s+", " ");
            }
          }

          if (title != null && description != null && !title.equals(description)) {
            node.setTag("name", title);
            node.setTag("note:description", description);

          } else if (title != null) {
            node.setTag("name", title);

          }

          node.setTag("source", "api.karta.orebro.se");
          node.setTag("source:url", feature.getProperties().getString("rel"));

          if (feature.getProperties().has("url")) {
            String url = feature.getProperties().getString("url").trim();
            if (!url.isEmpty()) {
              node.setTag("url", url);
            }
          }

          root.add(node);

        }

        OsmXmlWriter osmXmlWriter = new OsmXmlWriter(new OutputStreamWriter(new FileOutputStream("osm.xml/" + id + "-" + layer.getString("title") + ".osm.xml"), "UTF8"));
        osmXmlWriter.write(root);
        osmXmlWriter.close();

        System.currentTimeMillis();
      }
    }

  }

  public JSONArray getLayers() throws Exception {
    CloseableHttpClient client = HttpClientBuilder.create().build();

    CloseableHttpResponse response = client.execute(new HttpGet("http://data.karta.orebro.se/api/v1/layers"));

    String json = IOUtils.toString(response.getEntity().getContent(), "UTF8");

    response.close();

    client.close();

    return new JSONArray(new JSONTokener(json));

  }

  public JSONArray exportLayers(JSONArray layers) throws Exception {
    JSONArray output = new JSONArray();

    for (int i = 0; i < layers.length(); i++) {
      JSONObject inputLayer = layers.getJSONObject(i);

      if ("vectorLayer".equals(inputLayer.getString("type"))) {

        JSONObject outputLayer = new JSONObject(new LinkedHashMap());
        outputLayer.put("id", inputLayer.getInt("id"));
        outputLayer.put("title", inputLayer.getString("title"));
        outputLayer.put("tags", new JSONObject());
        output.put(outputLayer);

      }


    }

    System.out.println(output);

    return output;

  }

  public FeatureCollection getLayer(int id) throws Exception {

    CloseableHttpClient client = HttpClientBuilder.create().build();

    CloseableHttpResponse response = client.execute(new HttpGet("http://data.karta.orebro.se/api/v1/layers/" + id + "?srs=EPSG:4326"));

    JSONObject json = new JSONObject(new JSONTokener(IOUtils.toString(response.getEntity().getContent(), "UTF8")));

    FeatureCollection featureCollection = GeoJSONParser.parseFeatureCollection(json);

    response.close();

    client.close();

    return featureCollection;

  }

}
