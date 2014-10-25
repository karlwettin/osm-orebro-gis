package se.kodapan.osm.orebro;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.json.JSONArray;
import org.json.JSONTokener;
import se.kodapan.geojson.Point;
import se.kodapan.osm.domain.Node;
import se.kodapan.osm.domain.root.PojoRoot;
import se.kodapan.osm.domain.root.indexed.IndexedRoot;
import se.kodapan.osm.domain.root.indexed.IndexedRootImpl;
import se.kodapan.osm.parser.xml.instantiated.InstantiatedOsmXmlParser;
import se.kodapan.osm.xml.OsmXmlWriter;

import java.io.*;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.*;

/**
 * @author kalle@kodapan.se
 * @since 2014-10-25 17:23
 */
public class Orebro {

  public final static String serverURLPrefix = "http://data.karta.orebro.se/api/v1/";

  public final static File data = new File("data");


  public static void main(String[] args) throws Exception {

    if (!data.exists()) {
      data.mkdirs();
    }

//    loadSweden();
//
//    if (true) {
//      return;
//    }

    OsmXmlWriter osmXmlWriter = new OsmXmlWriter(new OutputStreamWriter(new FileOutputStream(new File(data, "orebro.osm.xml")), "UTF8"));

//    harvestSingleCharacterJSON();
//    extractTypes();
//    extractPossibleStreetNames();

    Collection<Node> houseNumbers = extractHouseNumbers();
    int id = -1;
    for (Node node : houseNumbers) {
      node.setId(id--);
      osmXmlWriter.write(node);
    }
    osmXmlWriter.close();
  }


  public static Set<Node> extractHouseNumbers() throws Exception {


    Set<Node> houseNumbers = new HashSet<>();

    Search search = new Search();

    boolean dobreak = false;

    for (String streetName : extractPossibleStreetNames()) {
      if (dobreak) {
        break;
      }
      int houseNumber = 1;
      int previousFoundHouseNumber = 0;
      while (houseNumber - previousFoundHouseNumber < 25) {
        String textQuery = streetName + " " + houseNumber;


          JSONArray response = search.search(new File(data, "house numbers"), textQuery);

        if (response.length() != 0) {

          Item item = Search.unmarshallJSONItem(response.getJSONObject(0));
          if (textQuery.equalsIgnoreCase(item.getTitle())) {

            Node streetAddress = new Node();
            Point point = (Point)item.getGeom();
            streetAddress.setLatitude(point.getLatitude());
            streetAddress.setLongitude(point.getLongitude());
            streetAddress.setTag("addr:housenumber", String.valueOf(houseNumber));
            streetAddress.setTag("addr:street", streetName);
            if (item.getTown() != null) {
              streetAddress.setTag("addr:city", item.getTown());
            }
            houseNumbers.add(streetAddress);

            previousFoundHouseNumber = houseNumber;
          }
        }
        houseNumber++;
      }
    }

    return houseNumbers;
  }

  public static void extractTypes() throws Exception {

    Set<String> types = new HashSet<>();

    for (File file : data.listFiles(new FileFilter() {
      @Override
      public boolean accept(File pathname) {
        return pathname.getName().endsWith(".json");
      }
    })) {

      JSONArray response = new JSONArray(new JSONTokener(new InputStreamReader(new FileInputStream(file), "UTF8")));
      for (int i = 0; i < response.length(); i++) {
        types.add(Search.unmarshallJSONItem(response.getJSONObject(i)).getType());
      }
    }

    System.out.println(types);

  }

  public static Collection<String> extractPossibleStreetNames() throws Exception {

    harvestSingleCharacterJSON();

    Set<Item> items = new HashSet<>();

    for (File file : new File(data, "single character").listFiles(new FileFilter() {
      @Override
      public boolean accept(File pathname) {
        return pathname.getName().endsWith(".json");
      }
    })) {

      JSONArray response = new JSONArray(new JSONTokener(new InputStreamReader(new FileInputStream(file), "UTF8")));
      for (int i = 0; i < response.length(); i++) {
        items.add(Search.unmarshallJSONItem(response.getJSONObject(i)));
      }
    }

    Set<String> titles = new HashSet<>();
    for (Item item : items) {
      if ("StreetAddress".equals(item.getType())
          && item.getTitle() != null) {
        titles.add(item.getTitle());
      }
    }

    List<String> orderdTitles = new ArrayList<>(titles);
    Collections.sort(orderdTitles);
    Writer writer = new OutputStreamWriter(new FileOutputStream(new File(data, "streetNames.txt")), "UTF8");
    for (String title : orderdTitles) {
      writer.write(title);
      writer.write("\n");
    }
    writer.close();

    System.currentTimeMillis();

    return orderdTitles;
  }


  public static void harvestSingleCharacterJSON() throws Exception {


    Search search = new Search();

    char[] characters = {
        'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z', 'å', 'ä', 'ö',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9'
    };

    for (char character : characters) {
      search.search(new File(data, "single character"), String.valueOf(character));
    }


    search.close();


  }

  public static PojoRoot loadSweden() throws Exception {
    File file = new File(data, "sweden-latest.osm.bz2");
    if (!file.exists()) {
      URL website = new URL("http://mirror.openstreetmap.se/geofabrik/europe/sweden-latest.osm.bz2");
      ReadableByteChannel rbc = Channels.newChannel(website.openStream());
      FileOutputStream fos = new FileOutputStream(file);
      fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
      fos.close();
    }

    PojoRoot root = new PojoRoot();
    InstantiatedOsmXmlParser parser = InstantiatedOsmXmlParser.newInstance();
    parser.setRoot(root);

    parser.parse(new BZip2CompressorInputStream(new FileInputStream(file)));

    File path = new File(data, "sweden");
    boolean reconstruct = !path.exists();
    IndexedRoot indexedRoot = new IndexedRootImpl(root, path);
    indexedRoot.open();
    if (reconstruct) {
      indexedRoot.reconstruct(10);
    }

    return root;
  }


}
