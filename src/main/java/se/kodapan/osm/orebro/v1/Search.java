package se.kodapan.osm.orebro.v1;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import se.kodapan.geojson.Point;

import java.io.*;
import java.net.URLEncoder;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author kalle@kodapan.se
 * @since 2014-10-25 17:24
 */
public class Search {

  private CloseableHttpClient httpClient = HttpClientBuilder.create()
      .setUserAgent(getClass().getName())
      .build();

  public JSONArray search(File cachePath, String textQuery) throws Exception {

    if (!cachePath.exists() && !cachePath.mkdirs()) {
      throw new IOException("Could not mkdirs " + cachePath.getAbsolutePath());
    }

    File file = new File(cachePath, URLEncoder.encode(textQuery, "UTF8") + ".json");
    if (!file.exists()) {

      HttpGet get = new HttpGet(Orebro.serverURLPrefix + "search/" + URLEncoder.encode(textQuery, "UTF8").replace("+", "%20") + "?srs=epsg:4326");

      System.out.println(get.getURI());

      CloseableHttpResponse httpResponse = httpClient.execute(get);
      try {

        if (httpResponse.getStatusLine().getStatusCode() != 200) {
          throw new RuntimeException("HTTP " + httpResponse.getStatusLine().getStatusCode());
        }

        Writer out = new OutputStreamWriter(new FileOutputStream(file), "UTF8");
        new JSONArray(new JSONTokener(new InputStreamReader(httpResponse.getEntity().getContent(), "UTF8"))).write(out);
        out.close();


      } finally {
        httpResponse.close();
      }
    }

    return new JSONArray(new JSONTokener(new InputStreamReader(new FileInputStream(file), "UTF8")));

  }

  public void close() throws IOException {
    httpClient.close();
  }


  private static Pattern pointPattern = Pattern.compile("POINT\\s*\\(([0-9.]+) ([0-9.]+)\\)");

  public static Item unmarshallJSONItem(JSONObject json) throws Exception {
    if (json == null) {
      return null;
    }

    Item item = new Item();

    for (Iterator keys = json.keys(); keys.hasNext(); ) {

      String key = (String) keys.next();
      if ("layerId".equals(key)) {
        item.setLayerId(json.getInt(key));

      } else if ("title".equals(key)) {
        item.setTitle(json.getString(key).trim());

      } else if ("geom".equals(key)) {
        String geom = json.getString(key).trim();
        Matcher matcher;
        matcher = pointPattern.matcher(geom);
        if (matcher.matches()) {

          double longitude = Double.valueOf(matcher.group(1));
          double latitude = Double.valueOf(matcher.group(2));

          item.setGeom(new Point(longitude, latitude));

        } else {

          throw new RuntimeException("Unsupported geometry: " + geom);
        }


      } else if ("type".equals(key)) {

        // StreetAddress
        // RealEstate
        // GeoObject


        item.setType(json.getString(key).trim());

      } else if ("city".equals(key)) {
        item.setCity(json.getString(key).trim());

      } else if ("town".equals(key)) {
        item.setTown(json.getString(key).trim());

      } else {
        throw new RuntimeException("Unknown key " + key);
      }

    }

    return item;
  }


}
